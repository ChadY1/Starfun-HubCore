package com.example.hubcore.bank;

import com.example.hubcore.HubCorePlugin;
import com.example.hubcore.security.CryptoUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BankManager {

    private final HubCorePlugin plugin;
    private final CryptoUtil cryptoUtil;
    private final File folder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, BankAccount> cache = new ConcurrentHashMap<>();

    public BankManager(HubCorePlugin plugin, CryptoUtil cryptoUtil) {
        this.plugin = plugin;
        this.cryptoUtil = cryptoUtil;
        this.folder = new File(plugin.getDataFolder(), "bankdata");
        if (!folder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
        }
    }

    public BankAccount getOrCreate(UUID uuid, String name) {
        BankAccount account = cache.get(uuid);
        if (account == null) {
            account = load(uuid);
            if (account == null) account = new BankAccount(uuid, name);
            cache.put(uuid, account);
        }
        account.setName(name);
        return account;
    }

    public void save(BankAccount account) {
        cache.put(account.getUuid(), account);
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            File f = new File(folder, account.getUuid().toString() + ".dat");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                String json = gson.toJson(account);
                String out = json;
                if (cryptoUtil != null && plugin.getConfig().getBoolean("security.encryption.enabled", false)) {
                    try {
                        out = cryptoUtil.encrypt(json);
                    } catch (Exception ignored) {
                    }
                }
                writer.write(out);
            } catch (IOException e) {
                plugin.getLogger().severe("[Starfun/Bank] Failed to save account " + account.getUuid() + ": " + e.getMessage());
            }
        });
    }

    private BankAccount load(UUID uuid) {
        File f = new File(folder, uuid.toString() + ".dat");
        if (!f.exists()) return null;
        try {
            String content;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                content = sb.toString().trim();
            }
            if (cryptoUtil != null && plugin.getConfig().getBoolean("security.encryption.enabled", false)) {
                try { content = cryptoUtil.decrypt(content); } catch (Exception ignored) {}
            }
            BankAccount account = gson.fromJson(content, BankAccount.class);
            if (account != null && account.getUuid() == null) account.setUuid(uuid);
            return account;
        } catch (IOException e) {
            plugin.getLogger().severe("[Starfun/Bank] Failed to load account " + uuid + ": " + e.getMessage());
            return null;
        }
    }
}
