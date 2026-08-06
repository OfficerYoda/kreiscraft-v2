package de.kreiscraft.modules.effects;

import de.kreiscraft.core.event.PriorityListener;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Spawns a configurable particle trail behind players who have opted in via {@code /effects}.
 *
 * <p>Only fires when the player moves at least 0.1 blocks per event to avoid every-tick spam on
 * stationary players (distanceSquared &gt; 0.01).
 */
public final class EffectsListener implements PriorityListener {

    private final DefaultEffectsService service;

    public EffectsListener(DefaultEffectsService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.distanceSquared(to) <= 0.01) {
            return;
        }

        Player player = event.getPlayer();
        if (!service.isEnabled(player.getUniqueId())) {
            return;
        }

        EffectsConfig cfg = service.getConfig();
        // Use the 9-arg Paper overload so the operator-configured `force` flag
        // (send particle to players regardless of their particle settings) is honoured.
        // `extra` (speed) is 0.0 and data is null — correct for data-less particles.
        player.getWorld()
                .<Void>spawnParticle(
                        service.getParticle(player.getUniqueId()),
                        player.getLocation(),
                        cfg.count(),
                        cfg.offsetX(),
                        cfg.offsetY(),
                        cfg.offsetZ(),
                        0.0,
                        null,
                        cfg.force());
    }
}
