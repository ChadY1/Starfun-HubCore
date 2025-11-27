package com.example.hubcore.menu;

import com.example.hubcore.HubCorePlugin;
import com.example.hubcore.bungee.BungeeMessenger;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class GameMenuListener implements Listener {

    private final HubCorePlugin plugin;
    private final GameMenuManager menuManager;
    private final BungeeMessenger messenger;

    public GameMenuListener(HubCorePlugin plugin, GameMenuManager menuManager, BungeeMessenger messenger) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.messenger = messenger;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!menuManager.isMenu(event.getInventory())) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        String server = menuManager.getServerForSlot(slot);
        if (server != null) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Connexion au serveur " + server + "...");
            messenger.connectPlayer(player, server);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (menuManager.isOpenOnJoin()) {
            menuManager.openMenu(event.getPlayer());
        }
    }
}
