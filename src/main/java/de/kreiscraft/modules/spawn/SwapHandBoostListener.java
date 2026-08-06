package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.modules.spawn.SpawnConfig.SwapHandBoostConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.util.Vector;

/**
 * Applies a velocity boost in the player's facing direction when they swap
 * hand items while actively gliding with an elytra.
 *
 * <p>This feature was commented-out (dead code) in the legacy monolith and is
 * revived here as a first-class, configurable sub-feature.
 */
public final class SwapHandBoostListener implements PriorityListener {

    private final SwapHandBoostConfig config;

    public SwapHandBoostListener(SwapHandBoostConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isGliding())
            return;
        event.setCancelled(true);
        Vector direction = player.getLocation().getDirection()
                .multiply(config.vectorMultiplier());
        player.setVelocity(direction);
    }
}
