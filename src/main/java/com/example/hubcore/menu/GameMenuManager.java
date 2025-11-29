package com.example.hubcore.menu;

import com.example.hubcore.HubCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameMenuManager {

    private final HubCorePlugin plugin;
    private Inventory menu;
    private final Map<Integer, String> slotServers = new HashMap<>();
    private boolean enabled;
    private boolean openOnJoin;
    private String title;

    public GameMenuManager(HubCorePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("menu");
        if (sec == null) {
            enabled = false;
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        openOnJoin = sec.getBoolean("open-on-join", false);
        int size = sec.getInt("size", 27);
        title = ChatColor.translateAlternateColorCodes('&', sec.getString("title", "Games"));

        menu = Bukkit.createInventory(null, size, title);
        slotServers.clear();

        ConfigurationSection filler = sec.getConfigurationSection("filler");
        if (filler != null && filler.getBoolean("enabled", false)) {
            ItemStack fillerItem = buildItem(filler.getString("material", "GRAY_STAINED_GLASS_PANE"),
                    filler.getString("name", "&7"), null);
            if (fillerItem != null) {
                for (int i = 0; i < size; i++) {
                    menu.setItem(i, fillerItem);
                }
            }
        }

        ConfigurationSection items = sec.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection cs = items.getConfigurationSection(key);
                if (cs == null) continue;

                String material = cs.getString("material", "STONE");
                String name = cs.getString("name", key);
                List<String> lore = cs.getStringList("lore");
                ItemStack item = buildItem(material, name, lore);
                int slot = cs.getInt("slot", 0);
                String server = cs.getString("server", null);
                if (item != null && slot >= 0 && slot < size) {
                    menu.setItem(slot, item);
                    if (server != null) {
                        slotServers.put(slot, server);
                    }
                }
            }
        }
    }

    private ItemStack buildItem(String materialName, String displayName, List<String> lore) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Unknown material in menu: " + materialName);
            return null;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(colored);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean openMenu(Player player) {
        if (!enabled || menu == null) return false;
        player.openInventory(menu);
        return true;
    }

    public boolean openMenuCommand(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player player = (Player) sender;
        if (!enabled) {
            sender.sendMessage("Le menu est désactivé.");
            return true;
        }
        openMenu(player);
        return true;
    }

    public boolean isMenu(Inventory inventory) {
        return inventory != null && inventory.equals(menu);
    }

    public String getTitle() {
        return title;
    }

    public boolean isOpenOnJoin() {
        return openOnJoin;
    }

    public String getServerForSlot(int slot) {
        return slotServers.get(slot);
    }
}
