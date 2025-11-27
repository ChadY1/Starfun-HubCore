package com.example.hubcore.hub;

import com.example.hubcore.HubCorePlugin;
import com.example.hubcore.profile.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.Map;

public class HubManager implements Listener {

    private final HubCorePlugin plugin;
    private final PlayerProfileManager profileManager;
    private final Map<String, HubProfile> hubs = new HashMap<>();
    private String defaultHubName;
    private boolean teleportOnJoin;

    public HubManager(HubCorePlugin plugin, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        loadHubs();
    }

    private void loadHubs() {
        hubs.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("hubs");
        if (section == null) {
            plugin.getLogger().warning("No 'hubs' section in config.yml");
            return;
        }

        this.teleportOnJoin = section.getBoolean("teleport-on-join", true);
        this.defaultHubName = section.getString("default-hub", "main");

        ConfigurationSection profiles = section.getConfigurationSection("profiles");
        if (profiles == null) {
            plugin.getLogger().warning("No 'hubs.profiles' section in config.yml");
            return;
        }

        for (String key : profiles.getKeys(false)) {
            ConfigurationSection hs = profiles.getConfigurationSection(key);
            if (hs == null) continue;

            String worldName = hs.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Hub world '" + worldName + "' for profile " + key + " not found.");
                continue;
            }

            double x = hs.getDouble("x", 0.5);
            double y = hs.getDouble("y", 80.0);
            double z = hs.getDouble("z", 0.5);
            float yaw = (float) hs.getDouble("yaw", 0.0);
            float pitch = (float) hs.getDouble("pitch", 0.0);

            Location loc = new Location(world, x, y, z, yaw, pitch);
            hubs.put(key.toLowerCase(), new HubProfile(key, loc));
        }

        if (!hubs.containsKey(defaultHubName.toLowerCase())) {
            plugin.getLogger().warning("Default hub '" + defaultHubName + "' not found, picking first available.");
            if (!hubs.isEmpty()) {
                defaultHubName = hubs.values().iterator().next().getName();
            }
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public HubProfile getHub(String name) {
        if (name == null) name = defaultHubName;
        return hubs.get(name.toLowerCase());
    }

    public String getDefaultHubName() {
        return defaultHubName;
    }

    public boolean teleportToHub(Player player, String hubName) {
        HubProfile profile = getHub(hubName);
        if (profile == null) return false;
        player.teleport(profile.getLocation());
        if (profileManager != null) {
            profileManager.setSelectedHub(player.getUniqueId(), profile.getName());
        }
        return true;
    }

    public void teleportToDefaultHub(Player player) {
        teleportToHub(player, defaultHubName);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!teleportOnJoin) return;
        teleportToDefaultHub(event.getPlayer());
    }
}
