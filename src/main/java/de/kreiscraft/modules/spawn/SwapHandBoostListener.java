package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.event.PriorityListener;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.util.Vector;

/**
 * Applies a velocity boost in the player's facing direction when they swap
 * hand items while actively gliding with an elytra.
 *
 * <p>Config is read fresh on every event via a {@link Supplier} so that
 * {@code onConfigReload} in {@link SpawnModule} takes effect without restart.
 *
 * <p>This feature was commented-out (dead code) in the legacy monolith and is
 * revived here as a first-class, configurable sub-feature.
 */
public final class SwapHandBoostListener implements PriorityListener {

    private final Supplier<SpawnConfig> configSupply;

    public SwapHandBoostListener(Supplier<SpawnConfig> configSupply) {
        this.configSupply = configSupply;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isGliding()) {
            return;
        }
        SpawnConfig.SwapHandBoostConfig cfg = configSupply.get().swapHandBoost();
        event.setCancelled(true);
        Vector direction = player.getLocation().getDirection()
                .multiply(cfg.vectorMultiplier());
        player.setVelocity(direction);
    }
}
