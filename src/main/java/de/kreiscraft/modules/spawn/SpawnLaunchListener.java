package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.modules.spawn.SpawnConfig.SpawnLaunchConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Applies a configurable velocity vector when a player steps onto the trigger
 * block coordinate defined in {@link SpawnLaunchConfig}.
 *
 * <p>The launch fires once per entry — it does not repeatedly apply the
 * velocity while the player remains on the trigger block.
 *
 * <p>This feature was commented-out (dead code) in the legacy monolith and is
 * revived here as a first-class, configurable sub-feature.
 *
 * <p>Legacy bug fixed: the hardcoded coordinates have been replaced by the
 * {@code spawnLaunch.trigger} config field.
 */
public final class SpawnLaunchListener implements PriorityListener {

    private final SpawnLaunchConfig config;

    public SpawnLaunchListener(SpawnLaunchConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.trigger().isSet())
            return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null)
            return;

        int tx = (int) Math.floor(config.trigger().x());
        int ty = (int) Math.floor(config.trigger().y());
        int tz = (int) Math.floor(config.trigger().z());

        // Trigger fires only when the player ENTERS the block (not while standing on it).
        boolean fromOnTrigger = blockMatches(from, tx, ty, tz);
        boolean toOnTrigger = blockMatches(to, tx, ty, tz);

        if (!fromOnTrigger && toOnTrigger) {
            Player player = event.getPlayer();
            player.setVelocity(config.velocity().toVector());
        }
    }

    private static boolean blockMatches(Location loc, int bx, int by, int bz) {
        return loc.getBlockX() == bx && loc.getBlockY() == by && loc.getBlockZ() == bz;
    }
}
