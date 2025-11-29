package com.example.hubcore.commands;

import com.example.hubcore.HubCorePlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class StarfunReloadCommand implements CommandExecutor {

    private final HubCorePlugin plugin;

    public StarfunReloadCommand(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("starfun.admin.reload")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }
        plugin.reloadStarfun();
        sender.sendMessage(ChatColor.GREEN + "Configuration Starfun rechargée.");
        return true;
    }
}
