package com.example.hubcore.bungee;

import com.example.hubcore.HubCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageRecipient;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BungeeMessenger {

    private final HubCorePlugin plugin;
    private final boolean enabled;
    private final String channel;

    public BungeeMessenger(HubCorePlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("bungeecord.enabled", true);
        this.channel = plugin.getConfig().getString("bungeecord.channel", "BungeeCord");
    }

    public void connectPlayer(Player player, String server) {
        if (!enabled) {
            plugin.getLogger().warning("Bungeecord messaging disabled in config; cannot connect player to " + server);
            return;
        }
        if (player == null || server == null) return;
        sendPluginMessage(player, "Connect", server);
    }

    private void sendPluginMessage(PluginMessageRecipient recipient, String subChannel, String argument) {
        if (!enabled) return;
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteOut)) {
            out.writeUTF(subChannel);
            out.writeUTF(argument);
            recipient.sendPluginMessage(plugin, channel, byteOut.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to send plugin message: " + e.getMessage());
        }
    }
}
