package com.example.hubcore.hub;

import org.bukkit.Location;

public class HubProfile {

    private final String name;
    private final Location location;

    public HubProfile(String name, Location location) {
        this.name = name;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }
}
