package de.kreiscraft;

import org.bukkit.plugin.java.JavaPlugin;

public final class Kreiscraft extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Kreiscraft loading (foundation build — 0 modules)");
    }

    @Override
    public void onDisable() {
        getLogger().info("Kreiscraft unloading");
    }
}
