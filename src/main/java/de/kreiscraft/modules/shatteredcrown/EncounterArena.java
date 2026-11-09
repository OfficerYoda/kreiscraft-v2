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

    boolean laneSafe(BreathLane lane, List<BreathLane> existing, List<Player> participants) {
        Location arrival = new Location(center.getWorld(), center.getX() + config.arrivalX(), lane.center.getY(),
                center.getZ() + config.arrivalZ());
        if (Math.abs(lane.center.getY() - config.arrivalY()) <= config.arrivalHeight() / 2 + 3
                && lane.contains(arrival, config.arrivalRadius())) {
            return false;
        }
        // Sample the whole footprint: void, protection or an unusable raised surface is not an arena.
        int lengthSteps = (int) Math.ceil(lane.length);
        int widthSteps = (int) Math.ceil(lane.width);
        for (int x = 0; x <= lengthSteps; x++) {
            double along = -lane.length / 2 + x * lane.length / lengthSteps;
            for (int z = 0; z <= widthSteps; z++) {
                double across = -lane.width / 2 + z * lane.width / widthSteps;
                if (!walkable(lane.point(along, across))) {
                    return false;
                }
            }
        }
        double search = lane.length + config.laneWarningSeconds() * 3 + 32;
        var clouds = lane.center.getWorld().getNearbyEntities(lane.center, search, 6, search).stream()
                .filter(entity -> entity instanceof AreaEffectCloud cloud && cloud.isValid()
                        && cloud.getParticle() == Particle.DRAGON_BREATH)
                .map(entity -> (AreaEffectCloud) entity).limit(33).toList();
        if (clouds.size() > 32 || clouds.stream().anyMatch(cloud -> !Float.isFinite(cloud.getRadius())
                || cloud.getRadius() > 32 || lane.contains(cloud.getLocation(), cloud.getRadius() + 1))
                || existing.stream().anyMatch(other -> rectanglesOverlap(lane, other))) {
            return false;
        }
        // Do not add pressure while somebody is already escaping existing danger.
        if (participants.stream()
                .anyMatch(player -> existing.stream().anyMatch(other -> other.contains(player.getLocation(), 1))
                        || clouds.stream().anyMatch(cloud -> overlaps(player.getLocation(), 0.5, cloud)))) {
            return false;
        }
        return participants.stream().filter(player -> lane.contains(player.getLocation(), 1))
                .allMatch(player -> laneEscape(player.getLocation(), lane, existing, clouds));
    }

    boolean areaSafe(HuntArea area, List<HuntArea> existing, List<BreathLane> lanes, List<Player> participants,
            double warningSeconds) {
        Location arrival = new Location(center.getWorld(), center.getX() + config.arrivalX(), area.center.getY(),
                center.getZ() + config.arrivalZ());
        if (Math.abs(area.center.getY() - config.arrivalY()) <= config.arrivalHeight() / 2 + 3
                && area.contains(arrival, config.arrivalRadius())) {
            return false;
        }
        double search = area.radius + warningSeconds * 3 + 32;
        var clouds = area.center.getWorld().getNearbyEntities(area.center, search, 6, search).stream()
                .filter(entity -> entity instanceof AreaEffectCloud cloud && cloud.isValid()
                        && cloud.getParticle() == Particle.DRAGON_BREATH)
                .map(entity -> (AreaEffectCloud) entity).limit(33).toList();
        if (clouds.size() > 32 || clouds.stream().anyMatch(cloud -> !Float.isFinite(cloud.getRadius())
                || cloud.getRadius() > 32 || overlaps(area.center, area.radius + 1, cloud))) {
            return false;
        }
        for (double x = -area.radius; x <= area.radius; x += 0.5) {
            for (double z = -area.radius; z <= area.radius; z += 0.5) {
                Location spot = area.center.clone().add(x, 0, z);
                if (area.contains(spot, 0) && (!walkable(spot)
                        || existing.stream().anyMatch(other -> other.contains(spot, 1))
                        || lanes.stream().anyMatch(lane -> lane.contains(spot, 1)))) {
                    return false;
                }
            }
        }
        return participants.stream().filter(player -> area.contains(player.getLocation(), 1)).allMatch(player -> {
            for (int direction = 0; direction < 16; direction++) {
                double angle = direction * Math.PI * 2 / 16;
                for (double distance = 0; distance <= warningSeconds * 3; distance += 0.5) {
                    Location step = player.getLocation().clone().add(Math.cos(angle) * distance, 0,
                            Math.sin(angle) * distance);
                    if (!walkable(step) || existing.stream().anyMatch(other -> other.contains(step, 0.5))
                            || lanes.stream().anyMatch(lane -> lane.contains(step, 0.5))
                            || clouds.stream().anyMatch(cloud -> overlaps(step, 0.5, cloud))) {
                        break;
                    }
                    if (!area.contains(step, 1)) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    boolean landingSafe() {
        // Native landing uses the real podium. Require ground-level melee approaches, not a void perch.
        int distance = (int) Math.min(8, config.landingRadius() - 3);
        for (int[] offset : List.of(new int[] {distance, 0}, new int[] {0, distance}, new int[] {-distance, 0},
                new int[] {0, -distance})) {
            Location approach = anchorGround(center.getBlockX() + offset[0], center.getBlockZ() + offset[1], List.of());
            if (approach == null || Math.sqrt(horizontalSquared(approach, center.getX(), center.getZ())) + 3 > config
                    .landingRadius()) {
                return false;
            }
        }
        double reach = config.landingRadius() + 32;
        var clouds = center.getWorld().getNearbyEntities(center, reach, 32, reach).stream()
                .filter(entity -> entity instanceof AreaEffectCloud cloud && cloud.isValid()
                        && cloud.getParticle() == Particle.DRAGON_BREATH)
                .map(entity -> (AreaEffectCloud) entity).limit(33).toList();
        return clouds.size() <= 32 && clouds.stream().noneMatch(cloud -> !Float.isFinite(cloud.getRadius())
                || cloud.getRadius() > 32 || horizontalSquared(cloud.getLocation(), center.getX(),
                        center.getZ()) <= Math.pow(config.landingRadius() + cloud.getRadius(), 2));
    }

    boolean atPodium(Location dragon) {
        return contains(dragon) && horizontalSquared(dragon, center.getX(), center.getZ()) <= 64
                && Math.abs(dragon.getY() - center.getY()) <= 8;
    }

    private boolean rectanglesOverlap(BreathLane lane, BreathLane other) {
        return Math.abs(lane.center.getY() - other.center.getY()) <= 6
                && Math.abs(lane.center.getX() - other.center.getX()) <= (lane.eastWest ? lane.length : lane.width) / 2
                        + (other.eastWest ? other.length : other.width) / 2 + 1
                && Math.abs(lane.center.getZ() - other.center.getZ()) <= (lane.eastWest ? lane.width : lane.length) / 2
                        + (other.eastWest ? other.width : other.length) / 2 + 1;
    }

    private boolean laneEscape(Location start, BreathLane lane, List<BreathLane> existing,
            List<AreaEffectCloud> clouds) {
        for (int direction = 0; direction < 16; direction++) {
            double angle = direction * Math.PI * 2 / 16;
            for (double distance = 0; distance <= config.laneWarningSeconds() * 3; distance += 0.5) {
                Location step = start.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                if (!walkable(step) || existing.stream().anyMatch(other -> other.contains(step, 0.5))
                        || clouds.stream().anyMatch(cloud -> overlaps(step, 0.5, cloud))) {
                    break;
                }
                if (!lane.contains(step, 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double horizontalSquared(Location location, double x, double z) {
        double dx = location.getX() - x;
        double dz = location.getZ() - z;
        return dx * dx + dz * dz;
    }
}
