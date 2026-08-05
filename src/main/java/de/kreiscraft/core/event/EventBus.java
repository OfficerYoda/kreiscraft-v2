package de.kreiscraft.core.event;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class EventBus {

    private final Plugin plugin;
    private final List<Listener> registered = new ArrayList<>();

    public EventBus(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        registered.add(listener);
    }

    public void unregisterAll() {
        for (Listener l : registered) {
            HandlerList.unregisterAll(l);
        }
        registered.clear();
    }
}
