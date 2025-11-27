package com.example.hubcore.chat;

import com.example.hubcore.HubCorePlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;

public class ChatFormatterListener implements Listener {

    private final HubCorePlugin plugin;

    public ChatFormatterListener(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        ConfigurationSection chat = plugin.getConfig().getConfigurationSection("chat");
        if (chat == null || !chat.getBoolean("enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        String prefix = chat.getString("default-prefix", "");
        String nameColor = "";

        ConfigurationSection ranks = chat.getConfigurationSection("ranks");
        if (ranks != null) {
            for (Map.Entry<String, Object> entry : ranks.getValues(false).entrySet()) {
                if (!(entry.getValue() instanceof ConfigurationSection section)) continue;
                String perm = entry.getKey();
                if (player.hasPermission(perm)) {
                    prefix = section.getString("prefix", prefix);
                    nameColor = section.getString("name-color", nameColor);
                    break;
                }
            }
        }

        String format = chat.getString("format", "%player%: %message%");
        String coloredPrefix = ChatColor.translateAlternateColorCodes('&', prefix);
        String coloredName = ChatColor.translateAlternateColorCodes('&', nameColor) + player.getName();
        String coloredMessage = ChatColor.translateAlternateColorCodes('&', event.getMessage());

        format = format.replace("%player%", coloredPrefix + ChatColor.RESET + " " + coloredName)
                .replace("%message%", coloredMessage);
        event.setFormat(format);
    }
}
