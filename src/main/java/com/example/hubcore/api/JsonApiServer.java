package com.example.hubcore.api;

import com.example.hubcore.HubCorePlugin;
import com.example.hubcore.bank.BankService;
import com.example.hubcore.hub.HubManager;
import com.example.hubcore.security.CryptoUtil;
import com.example.hubcore.profile.PlayerProfile;
import com.example.hubcore.profile.PlayerProfileManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class JsonApiServer implements Listener {

    private final HubCorePlugin plugin;
    private final HubManager hubManager;
    private final CryptoUtil cryptoUtil;
    private final PlayerProfileManager profileManager;
    private final BankService bankService;

    private HttpServer server;

    private volatile int onlineCount = 0;
    private final Set<String> onlineNames = ConcurrentHashMap.newKeySet();

    private final String serverName;
    private final String motd;

    private final boolean requireToken;
    private final String tokenHeader;
    private final String tokenValue;
    private final boolean encryptionEnabled;

    public JsonApiServer(HubCorePlugin plugin, HubManager hubManager, CryptoUtil cryptoUtil, PlayerProfileManager profileManager, BankService bankService) {
        this.plugin = plugin;
        this.hubManager = hubManager;
        this.cryptoUtil = cryptoUtil;
        this.profileManager = profileManager;
        this.bankService = bankService;

        this.serverName = plugin.getConfig().getString("general.server-name", "Hub-1");
        this.motd = plugin.getConfig().getString("general.motd", "Hub server");

        this.requireToken = plugin.getConfig().getBoolean("security.api.require-token", true);
        this.tokenHeader = plugin.getConfig().getString("security.api.token-header", "X-Starfun-Auth");
        this.tokenValue = plugin.getConfig().getString("security.api.token", "CHANGE_ME_TOKEN");
        this.encryptionEnabled =
                plugin.getConfig().getBoolean("security.encryption.enabled", false) && cryptoUtil != null;

        for (Player p : Bukkit.getOnlinePlayers()) {
            onlineNames.add(p.getName());
        }
        onlineCount = onlineNames.size();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("api.enabled", false)) {
            plugin.getLogger().info("JSON API disabled in config.");
            return;
        }

        String bind = plugin.getConfig().getString("api.bind-address", "0.0.0.0");
        int port = plugin.getConfig().getInt("api.port", 8080);
        String contextPath = plugin.getConfig().getString("api.context-path", "/hubcore/status");
        String profilePath = plugin.getConfig().getString("api.profile-path", "/hubcore/profile");
        String bankPath = plugin.getConfig().getString("api.bank-path", "/hubcore/bank");
        boolean corsEnabled = plugin.getConfig().getBoolean("api.cors-enabled", true);

        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            server.createContext(contextPath, exchange -> handleStatus(exchange, corsEnabled));
            server.createContext(profilePath, exchange -> handleProfile(exchange, corsEnabled));
            server.createContext(bankPath, exchange -> handleBank(exchange, corsEnabled));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            plugin.getLogger().info("JSON API started on " + bind + ":" + port + " [status=" + contextPath + ", profile=" + profilePath + ", bank=" + bankPath + "]");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start JSON API: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleStatus(HttpExchange exchange, boolean corsEnabled) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        if (corsEnabled) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");

        if (requireToken) {
            String header = exchange.getRequestHeaders().getFirst(tokenHeader);
            if (header == null || !header.equals(tokenValue)) {
                String resp = "{\"error\":\"unauthorized\"}";
                byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                exchange.close();
                return;
            }
        }

        String json = buildStatusJson();
        String body;

        if (encryptionEnabled) {
            try {
                String encrypted = cryptoUtil.encrypt(json);
                body = "{\"encrypted\":true,\"payload\":\"" + escape(encrypted) + "\"}";
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to encrypt API response: " + e.getMessage());
                body = "{\"encrypted\":false,\"error\":\"encryption_failed\"}";
            }
        } else {
            body = json;
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private String buildStatusJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"serverName\":\"").append(escape(serverName)).append("\",");
        sb.append("\"motd\":\"").append(escape(motd)).append("\",");
        sb.append("\"onlinePlayers\":").append(onlineCount).append(",");
        sb.append("\"players\":[");
        boolean first = true;
        for (String name : onlineNames) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(name)).append("\"");
            first = false;
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String readJsonBody(HttpExchange exchange) throws IOException {
        String body = new String(readFully(exchange.getRequestBody()), StandardCharsets.UTF_8);
        if (!encryptionEnabled) return body;
        try {
            JsonObject wrapper = JsonParser.parseString(body).getAsJsonObject();
            if (wrapper.has("encrypted") && wrapper.get("encrypted").getAsBoolean()) {
                String enc = wrapper.get("payload").getAsString();
                return cryptoUtil.decrypt(enc);
            }
            return wrapper.has("payload") ? wrapper.get("payload").getAsString() : body;
        } catch (Exception e) {
            plugin.getLogger().severe("[Starfun/API] Failed to decrypt body: " + e.getMessage());
            throw new IOException("decrypt_failed", e);
        }
    }

    private byte[] readFully(InputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                try {
                    String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
                    params.put(key, value);
                } catch (UnsupportedEncodingException ignored) {
                }
            }
        }
        return params;
    }

    private void handleProfile(HttpExchange exchange, boolean corsEnabled) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        if (corsEnabled) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");

        if (requireToken) {
            String header = exchange.getRequestHeaders().getFirst(tokenHeader);
            if (header == null || !header.equals(tokenValue)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        String uuidParam = params.get("uuid");
        String nameParam = params.get("player");

        PlayerProfile profile = null;
        if (uuidParam != null) {
            try {
                profile = profileManager.getProfile(java.util.UUID.fromString(uuidParam));
            } catch (IllegalArgumentException ignored) {
                sendJson(exchange, 400, "{\"error\":\"invalid_uuid\"}");
                return;
            }
        } else if (nameParam != null) {
            Collection<PlayerProfile> snapshot = profileManager.getCacheSnapshot();
            for (PlayerProfile p : snapshot) {
                if (p.getName() != null && p.getName().equalsIgnoreCase(nameParam)) {
                    profile = p;
                    break;
                }
            }
        }

        if (profile == null) {
            sendJson(exchange, 404, wrapMaybeEncrypted("{\"error\":\"profile_not_found\"}"));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"uuid\":\"").append(escape(profile.getUuid().toString())).append("\",");
        sb.append("\"name\":\"").append(escape(profile.getName())).append("\",");
        sb.append("\"lastIp\":\"").append(escape(profile.getLastIp())).append("\",");
        sb.append("\"createdAt\":").append(profile.getCreatedAt()).append(",");
        sb.append("\"lastLogin\":").append(profile.getLastLogin()).append(",");
        sb.append("\"registered\":").append(profile.isRegistered()).append(",");
        sb.append("\"selectedHub\":\"").append(escape(profile.getSelectedHub() == null ? "" : profile.getSelectedHub())).append("\"");
        sb.append("}");

        sendJson(exchange, 200, wrapMaybeEncrypted(sb.toString()));
    }

    private String wrapMaybeEncrypted(String json) {
        if (encryptionEnabled) {
            try {
                String encrypted = cryptoUtil.encrypt(json);
                return "{\"encrypted\":true,\"payload\":\"" + escape(encrypted) + "\"}";
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to encrypt API response: " + e.getMessage());
                return "{\"encrypted\":false,\"error\":\"encryption_failed\"}";
            }
        }
        return json;
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private void handleBank(HttpExchange exchange, boolean corsEnabled) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        if (corsEnabled) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");

        if (requireToken) {
            String header = exchange.getRequestHeaders().getFirst(tokenHeader);
            if (header == null || !header.equals(tokenValue)) {
                byte[] bytes = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                exchange.close();
                return;
            }
        }

        String payload;
        try {
            payload = readJsonBody(exchange);
        } catch (IOException e) {
            byte[] bytes = "{\"error\":\"decrypt_failed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
            return;
        }

        JsonObject request;
        try {
            request = JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception e) {
            byte[] bytes = "{\"error\":\"bad_json\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            exchange.close();
            return;
        }

        String action = request.has("action") ? request.get("action").getAsString() : "";
        String uuidStr = request.has("uuid") ? request.get("uuid").getAsString() : null;
        String name = request.has("player") ? request.get("player").getAsString() : "unknown";
        long amount = request.has("amount") ? request.get("amount").getAsLong() : 0L;

        if (uuidStr == null) {
            byte[] bytes = "{\"error\":\"missing_uuid\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            exchange.close();
            return;
        }

        JsonObject response = new JsonObject();
        try {
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
            long newBalance;
            if ("deposit".equalsIgnoreCase(action)) {
                newBalance = bankService.deposit(uuid, name, amount);
            } else if ("withdraw".equalsIgnoreCase(action)) {
                newBalance = bankService.withdraw(uuid, name, amount);
            } else if ("balance".equalsIgnoreCase(action)) {
                newBalance = bankService.balance(uuid, name);
            } else {
                byte[] bytes = "{\"error\":\"unknown_action\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                exchange.close();
                return;
            }
            response.addProperty("uuid", uuid.toString());
            response.addProperty("balance", newBalance);
            response.addProperty("currency", "STAR");
            response.addProperty("action", action.toLowerCase());
        } catch (Exception e) {
            plugin.getLogger().severe("[Starfun/API] Bank error: " + e.getMessage());
            byte[] bytes = "{\"error\":\"bank_failure\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            exchange.close();
            return;
        }

        String respJson = response.toString();
        if (encryptionEnabled) {
            try {
                String enc = cryptoUtil.encrypt(respJson);
                respJson = "{\"encrypted\":true,\"payload\":\"" + escape(enc) + "\"}";
            } catch (Exception e) {
                plugin.getLogger().severe("[Starfun/API] Failed to encrypt bank response: " + e.getMessage());
            }
        }

        byte[] bytes = respJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        onlineNames.add(event.getPlayer().getName());
        onlineCount = onlineNames.size();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onlineNames.remove(event.getPlayer().getName());
        onlineCount = onlineNames.size();
    }
}
