package com.example.hubcore.hub;

import com.example.hubcore.HubCorePlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetHubCommand implements CommandExecutor {

    private final HubCorePlugin plugin;

    public SetHubCommand(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player player = (Player) sender;
        String hubName = args.length > 0 ? args[0] : plugin.getConfig().getString("hubs.default-hub", "main");
        Location loc = player.getLocation();
        String base = "hubs.profiles." + hubName;
        plugin.getConfig().set(base + ".world", loc.getWorld().getName());
        plugin.getConfig().set(base + ".x", loc.getX());
        plugin.getConfig().set(base + ".y", loc.getY());
        plugin.getConfig().set(base + ".z", loc.getZ());
        plugin.getConfig().set(base + ".yaw", loc.getYaw());
        plugin.getConfig().set(base + ".pitch", loc.getPitch());
        plugin.saveConfig();
        sender.sendMessage("Hub position updated for profile '" + hubName + "'.");
        return true;
    }
}
