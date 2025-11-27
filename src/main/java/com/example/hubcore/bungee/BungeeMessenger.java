package com.example.hubcore.bungee;

import com.example.hubcore.HubCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageRecipient;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BungeeMessenger {

    private final HubCorePlugin plugin;

    public BungeeMessenger(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void connectPlayer(Player player, String server) {
        if (player == null || server == null) return;
        sendPluginMessage(player, "Connect", server);
    }

    private void sendPluginMessage(PluginMessageRecipient recipient, String subChannel, String argument) {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteOut)) {
            out.writeUTF(subChannel);
            out.writeUTF(argument);
            recipient.sendPluginMessage(plugin, "BungeeCord", byteOut.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to send plugin message: " + e.getMessage());
        }
    }
}
