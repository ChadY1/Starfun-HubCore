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
    private PlayerProfileManager profileManager;
    private AuthManager authManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initSecurity();

        this.profileManager = new PlayerProfileManager(this, cryptoUtil);
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

        this.jsonApiServer = new JsonApiServer(this, hubManager, cryptoUtil);
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
        String key = getConfig().getString("security.encryption.key", null);
        if (key == null || key.equalsIgnoreCase("CHANGE_ME_GENERATED_BASE64_AES_KEY")) {
            getLogger().warning("No valid security.encryption.key set. Encryption disabled.");
            this.cryptoUtil = null;
            return;
        }
        try {
            this.cryptoUtil = new CryptoUtil(key);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize CryptoUtil: " + e.getMessage());
            this.cryptoUtil = null;
        }
    }

    public HubManager getHubManager() { return hubManager; }
    public PlayerProfileManager getProfileManager() { return profileManager; }
    public AuthManager getAuthManager() { return authManager; }
    public CryptoUtil getCryptoUtil() { return cryptoUtil; }
}
