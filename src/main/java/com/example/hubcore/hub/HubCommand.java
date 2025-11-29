package com.example.hubcore.hub;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HubCommand implements CommandExecutor {

    private final HubManager hubManager;

    public HubCommand(HubManager hubManager) {
        this.hubManager = hubManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player player = (Player) sender;
        String hubName = (args.length > 0) ? args[0] : null;

        boolean ok = hubManager.teleportToHub(player, hubName);
        if (!ok) {
            sender.sendMessage(ChatColor.RED + "Hub introuvable.");
        }
        return true;
    }
}
