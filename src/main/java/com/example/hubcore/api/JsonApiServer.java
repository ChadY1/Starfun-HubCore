package com.example.hubcore.api;

import com.example.hubcore.HubCorePlugin;
import com.example.hubcore.hub.HubManager;
import com.example.hubcore.security.CryptoUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class JsonApiServer implements Listener {

    private final HubCorePlugin plugin;
    private final HubManager hubManager;
    private final CryptoUtil cryptoUtil;

    private HttpServer server;

    private volatile int onlineCount = 0;
    private final Set<String> onlineNames = ConcurrentHashMap.newKeySet();

    private final String serverName;
    private final String motd;

    private final boolean requireToken;
    private final String tokenHeader;
    private final String tokenValue;
    private final boolean encryptionEnabled;

    public JsonApiServer(HubCorePlugin plugin, HubManager hubManager, CryptoUtil cryptoUtil) {
        this.plugin = plugin;
        this.hubManager = hubManager;
        this.cryptoUtil = cryptoUtil;

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
        boolean corsEnabled = plugin.getConfig().getBoolean("api.cors-enabled", true);

        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            server.createContext(contextPath, exchange -> handleStatus(exchange, corsEnabled));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            plugin.getLogger().info("JSON API started on " + bind + ":" + port + contextPath);
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
