package de.kreiscraft.modules.effects;

import de.kreiscraft.core.event.PriorityListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

/** Clears per-player state when a player disconnects to prevent unbounded map growth. */
public final class EffectsCleanupListener implements PriorityListener {

    private final DefaultEffectsService service;

    public EffectsCleanupListener(DefaultEffectsService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.removePlayer(event.getPlayer().getUniqueId());
    }
}
