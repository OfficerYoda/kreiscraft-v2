package de.kreiscraft.modules.shatteredcrown;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;

/** Frozen ground heights shared by a warning, flight path and damage footprint. */
final class AttackSurface {
    private final World world;
    private final double referenceY;
    private final Map<Long, Integer> heights = new HashMap<>();

    private AttackSurface(Location center) {
        this.world = center.getWorld();
        this.referenceY = center.getY();
    }

    static AttackSurface capture(Location center, double halfX, double halfZ,
            Predicate<Location> footprint, EncounterArena arena) {
        var surface = new AttackSurface(center);
        for (int x = (int) Math.floor(center.getX() - halfX - 1); x <= Math.floor(center.getX() + halfX + 1); x++) {
            for (int z = (int) Math.floor(center.getZ() - halfZ - 1); z <= Math.floor(center.getZ() + halfZ + 1); z++) {
                Location spot = new Location(center.getWorld(), x + 0.5, center.getY(), z + 0.5);
                if (!footprint.test(spot)) {
                    continue;
                }
                Location ground = column(spot, 6, 6);
                if (!arena.contains(spot)) {
                    arena.reject("surface outside combat area");
                    return null;
                }
                // ponytail: holes have no surface to project; surviving footing still defines valid attack geometry.
                if (ground == null) {
                    continue;
                }
                if (!arena.contains(ground)) {
                    arena.reject("surface rejected at " + x + "," + center.getBlockY() + "," + z
                            + ": outside combat area");
                    return null;
                }
                surface.heights.put(key(x, z), ground.getBlockY());
            }
        }
        if (surface.heights.isEmpty()) {
            arena.reject("no supported ground in attack footprint");
            return null;
        }
        return surface;
    }

    static Location column(Location reference, int up, int down) {
        int max = Math.min(reference.getWorld().getMaxHeight() - 2, reference.getBlockY() + up);
        int min = Math.max(reference.getWorld().getMinHeight() + 1, reference.getBlockY() - down);
        for (int y = max; y >= min; y--) {
            Location spot = reference.clone();
            spot.setY(y);
            if (footing(spot)) {
                return spot;
            }
        }
        return null;
    }

    private static boolean footing(Location spot) {
        return spot.getBlock().getRelative(0, -1, 0).getType().isSolid()
                && spot.getBlock().isEmpty() && spot.getBlock().getRelative(0, 1, 0).isEmpty();
    }

    Location project(Location spot) {
        Integer y = heights.get(key(spot.getBlockX(), spot.getBlockZ()));
        if (y == null) {
            return null;
        }
        Location result = spot.clone();
        result.setY(y);
        return result;
    }

    boolean containsHeight(Location spot) {
        Location ground = project(spot);
        // Expanded overlap checks may have a cloud center outside the captured footprint.
        return ground == null ? Math.abs(spot.getY() - referenceY) <= 9
                : Math.abs(spot.getY() - ground.getY()) <= 3;
    }

    boolean unchanged() {
        for (var entry : heights.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (long) entry.getKey();
            if (!footing(new Location(world, x, entry.getValue(), z))) {
                return false;
            }
        }
        return true;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
