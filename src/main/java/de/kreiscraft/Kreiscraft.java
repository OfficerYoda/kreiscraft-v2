package de.kreiscraft;

import org.bukkit.plugin.java.JavaPlugin;

public final class Kreiscraft extends JavaPlugin {

    private KreiscraftBootstrap bootstrap;

    @Override
    public void onEnable() {
        this.bootstrap = new KreiscraftBootstrap(this);
        try {
            bootstrap.start();
        } catch (Exception e) {
            getLogger().severe("failed to start Kreiscraft: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap != null)
            bootstrap.stop();
    }
}
