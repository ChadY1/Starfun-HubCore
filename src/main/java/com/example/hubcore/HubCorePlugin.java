package com.example.hubcore;

import com.example.hubcore.api.JsonApiServer;
import com.example.hubcore.auth.AuthManager;
import com.example.hubcore.auth.LoginCommand;
import com.example.hubcore.auth.RegisterCommand;
import com.example.hubcore.bungee.BungeeMessenger;
import com.example.hubcore.chat.ChatFormatterListener;
import com.example.hubcore.essentials.FlyCommand;
import com.example.hubcore.essentials.SpawnCommand;
import com.example.hubcore.hub.HubCommand;
import com.example.hubcore.hub.HubManager;
import com.example.hubcore.hub.SetHubCommand;
import com.example.hubcore.menu.GameMenuListener;
import com.example.hubcore.menu.GameMenuManager;
import com.example.hubcore.profile.PlayerProfileManager;
import com.example.hubcore.security.CryptoUtil;
import org.bukkit.plugin.java.JavaPlugin;

public class HubCorePlugin extends JavaPlugin {

    private HubManager hubManager;
    private GameMenuManager gameMenuManager;
    private BungeeMessenger bungeeMessenger;
    private JsonApiServer jsonApiServer;

    private CryptoUtil cryptoUtil;
    private CryptoUtil fallbackCryptoUtil;
    private PlayerProfileManager profileManager;
    private AuthManager authManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initSecurity();

        this.profileManager = new PlayerProfileManager(this, cryptoUtil, fallbackCryptoUtil);
        this.hubManager = new HubManager(this, profileManager);
        this.bungeeMessenger = new BungeeMessenger(this);
        this.gameMenuManager = new GameMenuManager(this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getServer().getPluginManager().registerEvents(new ChatFormatterListener(this), this);
        getServer().getPluginManager().registerEvents(new GameMenuListener(this, gameMenuManager, bungeeMessenger), this);

        this.authManager = new AuthManager(this, profileManager, hubManager);

        getCommand("hub").setExecutor(new HubCommand(hubManager));
        getCommand("sethub").setExecutor(new SetHubCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(hubManager));
        getCommand("fly").setExecutor(new FlyCommand());
        getCommand("games").setExecutor((sender, command, label, args) -> gameMenuManager.openMenuCommand(sender));

        getCommand("register").setExecutor(new RegisterCommand(authManager));
        getCommand("login").setExecutor(new LoginCommand(authManager));

        this.jsonApiServer = new JsonApiServer(this, hubManager, cryptoUtil, profileManager);
        jsonApiServer.start();

        getLogger().info("HubCore (Starfun) enabled.");
    }

    @Override
    public void onDisable() {
        if (jsonApiServer != null) {
            jsonApiServer.stop();
        }
        getLogger().info("HubCore (Starfun) disabled.");
    }

    private void initSecurity() {
        boolean rotate = getConfig().getBoolean("security.encryption.rotate-on-restart", true);
        String currentKey = getConfig().getString("security.encryption.key", "");
        String previousKey = getConfig().getString("security.encryption.previous-key", "");

        if (rotate) {
            // Keep a fallback so old encrypted data can still be read if the key changes at startup.
            if (isValidKey(currentKey)) {
                fallbackCryptoUtil = buildCrypto(currentKey, "previous-key");
                getConfig().set("security.encryption.previous-key", currentKey);
            }

            currentKey = generateRandomKey();
            getConfig().set("security.encryption.key", currentKey);
            saveConfig();
            getLogger().info("Generated a new AES key for this session (rotate-on-restart=true). You can disable rotation to pin a static key.");
        } else if (isValidKey(previousKey) && !previousKey.equals(currentKey)) {
            // Allow manual fallback when rotation is disabled but an old key still exists.
            fallbackCryptoUtil = buildCrypto(previousKey, "previous-key");
        }

        if (!isValidKey(currentKey)) {
            getLogger().warning("No valid security.encryption.key set. Encryption disabled.");
            this.cryptoUtil = null;
            return;
        }

        this.cryptoUtil = buildCrypto(currentKey, "key");

        if (cryptoUtil == null && fallbackCryptoUtil != null) {
            getLogger().warning("Primary AES key failed to initialize; falling back to previous-key for this session.");
            cryptoUtil = fallbackCryptoUtil;
            fallbackCryptoUtil = null;
        }
    }

    private boolean isValidKey(String key) {
        return key != null && !key.isBlank() && !key.equalsIgnoreCase("CHANGE_ME_GENERATED_BASE64_AES_KEY");
    }

    private CryptoUtil buildCrypto(String key, String label) {
        if (!isValidKey(key)) return null;
        try {
            return new CryptoUtil(key);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize CryptoUtil for " + label + ": " + e.getMessage());
            return null;
        }
    }

    private String generateRandomKey() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    public HubManager getHubManager() { return hubManager; }
    public PlayerProfileManager getProfileManager() { return profileManager; }
    public AuthManager getAuthManager() { return authManager; }
    public CryptoUtil getCryptoUtil() { return cryptoUtil; }
}
