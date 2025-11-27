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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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

    private final boolean bruteForceEnabled;
    private final int bruteForceMaxAttempts;
    private final int bruteForceCooldownSeconds;
    private final boolean ipLockEnabled;
    private final int ipLockSeconds;

    private final Map<String, Integer> ipAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> ipCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicInteger successfulLogins = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger failedLogins = new java.util.concurrent.atomic.AtomicInteger();

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
            bruteForceEnabled = false;
            bruteForceMaxAttempts = 5;
            bruteForceCooldownSeconds = 30;
            ipLockEnabled = false;
            ipLockSeconds = 0;
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

        ConfigurationSection brute = sec.getConfigurationSection("bruteforce");
        if (brute != null) {
            bruteForceEnabled = brute.getBoolean("enabled", true);
            bruteForceMaxAttempts = brute.getInt("max-attempts", 5);
            bruteForceCooldownSeconds = brute.getInt("cooldown-seconds", 30);
            ipLockEnabled = brute.getBoolean("ip-lock", false);
            ipLockSeconds = brute.getInt("ip-lock-seconds", 900);
        } else {
            bruteForceEnabled = false;
            bruteForceMaxAttempts = 5;
            bruteForceCooldownSeconds = 30;
            ipLockEnabled = false;
            ipLockSeconds = 0;
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
            player.getScheduler().runDelayed(plugin, task -> {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null && p.isOnline() && !isAuthenticated(p)) {
                    p.kickPlayer("Temps de connexion dépassé sur " + serverName + ".");
                }
            }, () -> {}, loginTimeoutSeconds * 20L);
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
            plugin.getLogger().log(Level.SEVERE, "Failed to register " + player.getName() + ": " + e.getMessage(), e);
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
            if (isLockedOut(player, profile)) {
                player.sendMessage(ChatColor.RED + "Trop de tentatives. Réessaie plus tard.");
                return true;
            }
            if (!hasher.verify(password, profile.getPasswordHash())) {
                markFailure(player, profile);
                player.sendMessage(ChatColor.RED + "Mot de passe incorrect.");
                return true;
            }
            profile.setLastLogin(System.currentTimeMillis());
            profileManager.saveProfile(profile);
            setAuthenticated(player, true);
            resetFailures(player, profile);
            successfulLogins.incrementAndGet();

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

    private boolean isLockedOut(Player player, PlayerProfile profile) {
        if (!bruteForceEnabled) return false;

        String ip = player.getAddress() != null && player.getAddress().getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "unknown";

        long now = System.currentTimeMillis();

        Long ipCooldown = ipCooldowns.get(ip);
        if (ipCooldown != null && ipCooldown > now) return true;

        Long playerCooldown = playerCooldowns.get(profile.getUuid());
        if (playerCooldown != null && playerCooldown > now) return true;

        if (ipLockEnabled && profile.getLastIp() != null && !profile.getLastIp().equalsIgnoreCase(ip)) {
            long lastLogin = profile.getLastLogin();
            if (lastLogin > 0 && (now - lastLogin) < (ipLockSeconds * 1000L)) {
                return true;
            }
        }
        return false;
    }

    private void markFailure(Player player, PlayerProfile profile) {
        failedLogins.incrementAndGet();
        if (!bruteForceEnabled) return;

        String ip = player.getAddress() != null && player.getAddress().getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "unknown";

        int ipCount = ipAttempts.getOrDefault(ip, 0) + 1;
        int playerCount = playerAttempts.getOrDefault(profile.getUuid(), 0) + 1;
        ipAttempts.put(ip, ipCount);
        playerAttempts.put(profile.getUuid(), playerCount);

        if (ipCount >= bruteForceMaxAttempts) {
            ipCooldowns.put(ip, System.currentTimeMillis() + bruteForceCooldownSeconds * 1000L);
        }
        if (playerCount >= bruteForceMaxAttempts) {
            playerCooldowns.put(profile.getUuid(), System.currentTimeMillis() + bruteForceCooldownSeconds * 1000L);
        }
        plugin.getLogger().warning("[Starfun/Auth] Failed login for " + player.getName() + " ip=" + ip + " count=" + playerCount);
    }

    private void resetFailures(Player player, PlayerProfile profile) {
        String ip = player.getAddress() != null && player.getAddress().getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "unknown";
        ipAttempts.remove(ip);
        playerAttempts.remove(profile.getUuid());
        ipCooldowns.remove(ip);
        playerCooldowns.remove(profile.getUuid());
    }
}
