package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.event.PriorityListener;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Applies a configurable velocity vector when a player steps onto the trigger
 * block coordinate defined in {@link SpawnConfig.SpawnLaunchConfig}.
 *
 * <p>The launch fires once per block-entry — it does not repeatedly apply the
 * velocity while the player remains on the trigger block.
 *
 * <p>Both world name AND block coordinates must match the configured trigger;
 * the launch is not applied in other dimensions even if coordinates coincide.
 *
 * <p>This feature was commented-out (dead code) in the legacy monolith and is
 * revived here as a first-class, configurable sub-feature.
 *
 * <p>Legacy bug fixed: the hardcoded coordinates have been replaced by the
 * {@code spawnLaunch.trigger} config field.
 */
public final class SpawnLaunchListener implements PriorityListener {

    private final Supplier<SpawnConfig> configSupply;

    public SpawnLaunchListener(Supplier<SpawnConfig> configSupply) {
        this.configSupply = configSupply;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        SpawnConfig.SpawnLaunchConfig cfg = configSupply.get().spawnLaunch();
        if (cfg.trigger() == null) {
            return;
        }

        // ── world guard ───────────────────────────────────────────────────
        Player player = event.getPlayer();
        if (cfg.trigger().getWorld() == null || !cfg.trigger().getWorld().equals(player.getWorld())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        int tx = (int) Math.floor(cfg.trigger().getX());
        int ty = (int) Math.floor(cfg.trigger().getY());
        int tz = (int) Math.floor(cfg.trigger().getZ());

        // Trigger fires only when the player ENTERS the block (not while standing on it).
        boolean fromOnTrigger = blockMatches(from, tx, ty, tz);
        boolean toOnTrigger = blockMatches(to, tx, ty, tz);

        if (!fromOnTrigger && toOnTrigger) {
            player.setVelocity(cfg.velocity());
        }
    }

    private static boolean blockMatches(Location loc, int bx, int by, int bz) {
        return loc.getBlockX() == bx && loc.getBlockY() == by && loc.getBlockZ() == bz;
    }
}
