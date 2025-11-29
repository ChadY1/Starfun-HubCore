package com.example.hubcore.profile;

import com.example.hubcore.HubCorePlugin;
import com.example.hubcore.security.CryptoUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;

public class PlayerProfileManager {

    private final HubCorePlugin plugin;
    private final CryptoUtil cryptoUtil;
    private final CryptoUtil fallbackCryptoUtil;
    private final File dataFolder;
    private final Gson gson;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public PlayerProfileManager(HubCorePlugin plugin, CryptoUtil cryptoUtil, CryptoUtil fallbackCryptoUtil) {
        this.plugin = plugin;
        this.cryptoUtil = cryptoUtil;
        this.fallbackCryptoUtil = fallbackCryptoUtil;
        String folderName = plugin.getConfig().getString("playerdata.folder", "playerdata");
        this.dataFolder = new File(plugin.getDataFolder(), folderName);
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().severe("Failed to create playerdata folder: " + dataFolder.getPath());
        }
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public PlayerProfile getProfile(UUID uuid, String name, String ip) {
        PlayerProfile profile = cache.get(uuid);
        if (profile == null) {
            profile = loadFromDisk(uuid);
            if (profile == null) {
                profile = new PlayerProfile(uuid, name);
            }
            cache.put(uuid, profile);
        }
        profile.setName(name);
        profile.setLastIp(ip);
        return profile;
    }

    public PlayerProfile getProfile(UUID uuid) {
        return cache.get(uuid);
    }

    public java.util.Collection<PlayerProfile> getCacheSnapshot() {
        return java.util.Collections.unmodifiableCollection(cache.values());
    }

    public void saveProfile(PlayerProfile profile) {
        cache.put(profile.getUuid(), profile);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                File file = new File(dataFolder, profile.getUuid().toString() + ".dat");
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    String json = gson.toJson(profile);
                    String output = json;
                    if (cryptoUtil != null && plugin.getConfig().getBoolean("security.encryption.enabled", false)) {
                        try {
                            output = cryptoUtil.encrypt(json);
                        } catch (Exception e) {
                            plugin.getLogger().severe("Failed to encrypt profile for " + profile.getName() + ": " + e.getMessage());
                            if (fallbackCryptoUtil != null) {
                                try {
                                    output = fallbackCryptoUtil.encrypt(json);
                                    plugin.getLogger().warning("Used previous-key to encrypt profile " + profile.getName() + " after primary key failure.");
                                } catch (Exception ignored) {
                                    output = json;
                                }
                            } else {
                                output = json;
                            }
                        }
                    }
                    writer.write(output);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to save profile " + profile.getName() + ": " + e.getMessage());
                }
            }
        });
    }

    public void setSelectedHub(UUID uuid, String hubName) {
        PlayerProfile profile = cache.get(uuid);
        if (profile == null) return;
        profile.setSelectedHub(hubName);
        saveProfile(profile);
    }

    private PlayerProfile loadFromDisk(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".dat");
        if (!file.exists()) return null;

        try {
            String content;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                content = sb.toString().trim();
            }

            if (cryptoUtil != null && plugin.getConfig().getBoolean("security.encryption.enabled", false)) {
                boolean decrypted = false;
                try {
                    content = cryptoUtil.decrypt(content);
                    decrypted = true;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to decrypt profile " + uuid + " with primary key: " + e.getMessage());
                }

                if (!decrypted && fallbackCryptoUtil != null) {
                    try {
                        content = fallbackCryptoUtil.decrypt(content);
                        decrypted = true;
                        plugin.getLogger().info("Profile " + uuid + " decrypted with previous-key after primary key failed.");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to decrypt profile " + uuid + " with previous-key: " + e.getMessage());
                    }
                }

                if (!decrypted) {
                    plugin.getLogger().warning("Proceeding with raw content for profile " + uuid + " (encryption enabled but no key succeeded).");
                }
            }

            PlayerProfile profile = gson.fromJson(content, PlayerProfile.class);
            if (profile == null) return null;
            if (profile.getUuid() == null) profile.setUuid(uuid);
            return profile;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load profile " + uuid + ": " + e.getMessage());
            return null;
        }
    }
}
