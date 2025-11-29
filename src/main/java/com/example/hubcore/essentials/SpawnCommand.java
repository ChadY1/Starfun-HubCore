package com.example.hubcore.essentials;

import com.example.hubcore.hub.HubManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final HubManager hubManager;

    public SpawnCommand(HubManager hubManager) {
        this.hubManager = hubManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player player = (Player) sender;
        hubManager.teleportToDefaultHub(player);
        return true;
    }
}
