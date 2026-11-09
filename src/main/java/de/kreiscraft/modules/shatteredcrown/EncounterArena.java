package de.kreiscraft.modules.shatteredcrown;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Particle;
import org.bukkit.entity.AreaEffectCloud;
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

    List<Location> openingGround() {
        List<Location> locations = new ArrayList<>();
        int distance = config.anchorDistance().intValue();
        for (int[] offset : List.of(new int[] {distance, 0}, new int[] {0, distance},
                new int[] {-distance, 0}, new int[] {0, -distance})) {
            Location ground = anchorGround(center.getBlockX() + offset[0], center.getBlockZ() + offset[1], locations);
            if (ground == null) {
                return List.of();
            }
            locations.add(ground);
        }
        return locations;
    }

    private Location anchorGround(int x, int z, List<Location> occupied) {
        World world = center.getWorld();
        for (int radius = 0; radius <= config.groundSearchRadius(); radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int y = world.getHighestBlockYAt(x + dx, z + dz) + 1;
                    Location candidate = new Location(world, x + dx + 0.5, y, z + dz + 0.5);
                    if (y <= config.groundMaxY() && usable(candidate)
                            && occupied.stream().noneMatch(other -> other.distanceSquared(candidate) <= 144)) {
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

    boolean inBombardment(Location location, Location impact, double radius) {
        return location.getWorld().equals(impact.getWorld())
                && horizontalSquared(location, impact.getX(), impact.getZ()) <= radius * radius
                && Math.abs(location.getY() - impact.getY()) <= 3;
    }

    boolean bombardmentSafe(Location impact, double radius, List<Player> participants) {
        double arrivalDistance = radius + config.arrivalRadius();
        if (!walkable(impact)
                || Math.abs(impact.getY() - config.arrivalY()) <= config.arrivalHeight() / 2 + 3
                        && horizontalSquared(impact, center.getX() + config.arrivalX(),
                                center.getZ() + config.arrivalZ()) <= arrivalDistance * arrivalDistance) {
            return false;
        }
        double search = radius + config.bombardmentWarningSeconds() * 3 + 32;
        var danger = impact.getWorld().getNearbyEntities(impact, search, 6, search).stream()
                .filter(entity -> entity instanceof AreaEffectCloud cloud && cloud.isValid()
                        && cloud.getParticle() == Particle.DRAGON_BREATH)
                .map(entity -> (AreaEffectCloud) entity).limit(33).toList();
        if (danger.size() > 32 || danger.stream().anyMatch(cloud -> !Float.isFinite(cloud.getRadius())
                || cloud.getRadius() > 32 || overlaps(impact, radius, cloud))) {
            return false;
        }
        return participants.stream().filter(player -> inBombardment(player.getLocation(), impact, radius))
                .allMatch(player -> escapeRoute(player.getLocation(), impact, radius, danger));
    }

    private boolean overlaps(Location location, double radius, AreaEffectCloud cloud) {
        Location pool = cloud.getLocation();
        double distance = radius + cloud.getRadius();
        return Math.abs(location.getY() - pool.getY()) <= 3
                && horizontalSquared(location, pool.getX(), pool.getZ()) <= distance * distance;
    }

    private boolean escapeRoute(Location start, Location impact, double radius, List<AreaEffectCloud> danger) {
        // Conservative straight walking corridors, bounded by warning time at 3 blocks/second.
        // No other bombardment can be pending: the controller serializes this opening pattern.
        double reach = config.bombardmentWarningSeconds() * 3;
        for (int direction = 0; direction < 16; direction++) {
            double angle = direction * Math.PI * 2 / 16;
            for (double distance = 0; distance <= reach; distance += 0.5) {
                Location step = start.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                if (!walkable(step) || danger.stream().anyMatch(cloud -> overlaps(step, 0.5, cloud))) {
                    break;
                }
                if (horizontalSquared(step, impact.getX(), impact.getZ()) > (radius + 1) * (radius + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean walkable(Location location) {
        // Check a normal player's body width, not just a point slipping through diagonal corners.
        for (double x : new double[] {-0.35, 0, 0.35}) {
            for (double z : new double[] {-0.35, 0, 0.35}) {
                Location spot = location.clone().add(x, 0, z);
                if (!contains(spot) || protectedArrival(spot)
                        || !spot.getBlock().getRelative(0, -1, 0).getType().isSolid()
                        || !spot.getBlock().isEmpty() || !spot.getBlock().getRelative(0, 1, 0).isEmpty()) {
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
