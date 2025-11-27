package com.example.hubcore.auth;

import com.example.hubcore.HubCorePlugin;
import com.example.hubcore.hub.HubManager;
import com.example.hubcore.profile.PlayerProfile;
import com.example.hubcore.profile.PlayerProfileManager;
import com.example.hubcore.security.PasswordHasher;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager implements Listener {

    private final HubCorePlugin plugin;
    private final PlayerProfileManager profileManager;
    private final HubManager hubManager;
    private final PasswordHasher hasher = new PasswordHasher();

    private final boolean enabled;
    private final String serverName;
    private final int loginTimeoutSeconds;

    private final boolean blockMovement;
    private final boolean blockChat;
    private final boolean blockCommands;
    private final Set<String> allowedCommands;

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    public AuthManager(HubCorePlugin plugin,
                       PlayerProfileManager profileManager,
                       HubManager hubManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.hubManager = hubManager;

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("auth");
        if (sec == null) {
            enabled = false;
            serverName = "Starfun";
            loginTimeoutSeconds = 60;
            blockMovement = blockChat = blockCommands = false;
            allowedCommands = Collections.emptySet();
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        serverName = sec.getString("server-name", "Starfun");
        loginTimeoutSeconds = sec.getInt("login-timeout-seconds", 60);

        ConfigurationSection prot = sec.getConfigurationSection("protections");
        if (prot != null) {
            blockMovement = prot.getBoolean("block-movement", true);
            blockChat = prot.getBoolean("block-chat", true);
            blockCommands = prot.getBoolean("block-commands", true);
            List<String> allowed = prot.getStringList("allowed-commands");
            Set<String> tmp = new HashSet<>();
            for (String s : allowed) {
                tmp.add(s.toLowerCase());
            }
            allowedCommands = tmp;
        } else {
            blockMovement = blockChat = blockCommands = false;
            allowedCommands = Collections.emptySet();
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isAuthenticated(Player player) {
        if (!enabled) return true;
        return authenticated.contains(player.getUniqueId());
    }

    private void setAuthenticated(Player player, boolean value) {
        if (!enabled) return;
        if (value) authenticated.add(player.getUniqueId());
        else authenticated.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = "unknown";
        InetSocketAddress addr = player.getAddress();
        if (addr != null && addr.getAddress() != null) {
            ip = addr.getAddress().getHostAddress();
        }

        PlayerProfile profile =
                profileManager.getProfile(player.getUniqueId(), player.getName(), ip);
        profileManager.saveProfile(profile);

        setAuthenticated(player, false);

        if (!enabled) return;

        if (!profile.isRegistered()) {
            player.sendMessage(ChatColor.GRAY + "Bienvenue sur " + ChatColor.GOLD + serverName + ChatColor.GRAY + " !");
            player.sendMessage(ChatColor.GRAY + "Veuillez vous enregistrer avec " + ChatColor.AQUA + "/register <motdepasse> <confirmation>");
        } else {
            player.sendMessage(ChatColor.GRAY + "Bienvenue sur " + ChatColor.GOLD + serverName + ChatColor.GRAY + " !");
            player.sendMessage(ChatColor.GRAY + "Veuillez vous connecter avec " + ChatColor.AQUA + "/login <motdepasse>");
        }

        if (loginTimeoutSeconds > 0) {
            final UUID uuid = player.getUniqueId();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null && p.isOnline() && !isAuthenticated(p)) {
                    p.kickPlayer("Temps de connexion dépassé sur " + serverName + ".");
                }
            }, loginTimeoutSeconds * 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        authenticated.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!enabled || !blockChat) return;
        Player player = event.getPlayer();
        if (!isAuthenticated(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous devez d'abord vous connecter sur " + serverName + ".");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled || !blockMovement) return;
        Player player = event.getPlayer();
        if (!isAuthenticated(player)) {
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled || !blockCommands) return;
        Player player = event.getPlayer();
        if (isAuthenticated(player)) return;

        String msg = event.getMessage();
        if (!msg.startsWith("/")) return;
        String base = msg.substring(1).split(" ")[0].toLowerCase();

        if (!allowedCommands.contains(base)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous devez d'abord vous authentifier sur " + serverName + ".");
        }
    }

    public boolean handleRegister(Player player, String password, String confirm) {
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "Le système d'authentification est désactivé.");
            return true;
        }
        PlayerProfile profile = profileManager.getProfile(player.getUniqueId(),
                player.getName(), player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown");

        if (profile.isRegistered()) {
            player.sendMessage(ChatColor.RED + "Vous êtes déjà enregistré. Utilisez /login.");
            return true;
        }
        if (!password.equals(confirm)) {
            player.sendMessage(ChatColor.RED + "Les mots de passe ne correspondent pas.");
            return true;
        }
        if (password.length() < 6) {
            player.sendMessage(ChatColor.RED + "Mot de passe trop court (min 6 caractères).");
            return true;
        }

        try {
            String hash = hasher.hash(password);
            profile.setPasswordHash(hash);
            profile.setRegistered(true);
            profile.setLastLogin(System.currentTimeMillis());
            profileManager.saveProfile(profile);
            setAuthenticated(player, true);

            player.sendMessage(ChatColor.GREEN + "Enregistrement réussi sur " + serverName + ".");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Erreur interne lors de l'enregistrement.");
        }
        return true;
    }

    public boolean handleLogin(Player player, String password) {
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "Le système d'authentification est désactivé.");
            return true;
        }
        PlayerProfile profile = profileManager.getProfile(player.getUniqueId(),
                player.getName(), player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown");

        if (!profile.isRegistered()) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas encore enregistré. Utilisez /register.");
            return true;
        }

        try {
            if (!hasher.verify(password, profile.getPasswordHash())) {
                player.sendMessage(ChatColor.RED + "Mot de passe incorrect.");
                return true;
            }
            profile.setLastLogin(System.currentTimeMillis());
            profileManager.saveProfile(profile);
            setAuthenticated(player, true);

            player.sendMessage(ChatColor.GREEN + "Connexion réussie sur " + serverName + ".");

            if (profile.getSelectedHub() != null) {
                boolean ok = hubManager.teleportToHub(player, profile.getSelectedHub());
                if (!ok) {
                    hubManager.teleportToDefaultHub(player);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to login " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Erreur interne lors de la connexion.");
        }
        return true;
    }
}
