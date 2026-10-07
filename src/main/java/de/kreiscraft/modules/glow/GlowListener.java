package de.kreiscraft.modules.glow;

import de.kreiscraft.core.event.PriorityListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Reconciles a joining player's persisted glow preference onto their entity, so entity state and
 * stored desire agree after a reconnect.
 */
public final class GlowListener implements PriorityListener {

    private final DefaultGlowService service;

    public GlowListener(DefaultGlowService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.reconcile(event.getPlayer());
    }
}
