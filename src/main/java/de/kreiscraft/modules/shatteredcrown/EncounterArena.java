package de.kreiscraft.modules.shatteredcrown;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Geometry is relative to the actual battle podium, never a legacy world name. */
final class EncounterArena {
    private final Location center;
    private final ShatteredCrownConfig config;

    EncounterArena(Location center, ShatteredCrownConfig config) {
        this.center = center.clone();
        this.config = config;
    }

    boolean participant(Player player) {
        return participantAt(player, player.getLocation(), player.getGameMode());
    }

    boolean participantAt(Player player, Location location, GameMode mode) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0
                && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && contains(location) && !protectedArrival(location);
    }

    boolean contains(Location location) {
        return center.getWorld().equals(location.getWorld())
                && horizontalSquared(location, center.getX(), center.getZ()) <= config.arenaRadius()
                        * config.arenaRadius()
                && location.getY() >= config.arenaMinY() && location.getY() <= config.arenaMaxY();
    }

    boolean protectedArrival(Location location) {
        return center.getWorld().equals(location.getWorld())
                && horizontalSquared(location, center.getX() + config.arrivalX(),
                        center.getZ() + config.arrivalZ()) <= config.arrivalRadius() * config.arrivalRadius()
                && Math.abs(location.getY() - config.arrivalY()) <= config.arrivalHeight() / 2;
    }

    Location anchorGround() {
        World world = center.getWorld();
        int x = center.getBlockX() + config.anchorDistance().intValue();
        int z = center.getBlockZ();
        for (int radius = 0; radius <= config.groundSearchRadius(); radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int y = world.getHighestBlockYAt(x + dx, z + dz) + 1;
                    Location candidate = new Location(world, x + dx + 0.5, y, z + dz + 0.5);
                    if (y <= config.groundMaxY() && usable(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean usable(Location location) {
        // Enough flat footing for the objective, defenders and a melee approach; never build terrain.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location spot = location.clone().add(x, 0, z);
                if (!contains(spot) || protectedArrival(spot)
                        || !spot.getBlock().getRelative(0, -1, 0).getType().isSolid()
                        || !spot.getBlock().isEmpty() || !spot.getBlock().getRelative(0, 1, 0).isEmpty()
                        || !spot.getBlock().getRelative(0, 2, 0).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double horizontalSquared(Location location, double x, double z) {
        double dx = location.getX() - x;
        double dz = location.getZ() - z;
        return dx * dx + dz * dz;
    }
}
