package com.example.hubcore.profile;

import java.util.UUID;

public class PlayerProfile {

    private UUID uuid;
    private String name;
    private String lastIp;
    private long createdAt;
    private long lastLogin;
    private boolean registered;
    private String passwordHash;
    private String selectedHub;

    public PlayerProfile() {
    }

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.registered = false;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSelectedHub() {
        return selectedHub;
    }

    public void setSelectedHub(String selectedHub) {
        this.selectedHub = selectedHub;
    }
}
