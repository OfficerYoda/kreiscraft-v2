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
    private String rejection = "no eligible target or clear flight path";

    void resetRejection() {
        rejection = "no eligible target or clear flight path";
    }

    String rejection() {
        return rejection;
    }

    boolean reject(String reason) {
        rejection = reason;
        return false;
    }

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
                && location.getY() >= center.getWorld().getMinHeight()
                && location.getY() < center.getWorld().getMaxHeight();
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
            Location fallback = center.clone().add(offset[0] + 0.5, 0, offset[1] + 0.5);
            locations.add(anchorSite(fallback));
        }
        return locations;
    }

    /** Fixed horizontal objective site; empty columns retain the last known placement height. */
    Location anchorSite(Location previous) {
        Location site = previous.clone();
        World world = center.getWorld();
        int highest = world.getHighestBlockYAt(site.getBlockX(), site.getBlockZ());
        if (highest >= world.getMinHeight()
                && !world.getBlockAt(site.getBlockX(), highest, site.getBlockZ()).isEmpty()) {
            site.setY(Math.min(world.getMaxHeight() - 3, highest + 1));
        }
        return site;
    }

    /** Defenders prefer nearby surviving footing; objectives themselves need no foundation. */
    Location defenderSite(Location home) {
        Location preferred = home.clone().add(2, 0, 0);
        World world = home.getWorld();
        for (int radius = 0; radius <= config.groundSearchRadius(); radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = preferred.getBlockX() + dx;
                    int z = preferred.getBlockZ() + dz;
                    Location feet = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
                    if (feet.getY() > world.getMinHeight() && feet.getY() < world.getMaxHeight() - 1
                            && walkable(feet)) {
                        return feet;
                    }
                }
            }
        }
        return preferred;
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
        var danger = nearbyBreath(impact, search, 6);
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
        if (lane.surface != null && !lane.surface.unchanged()) {
            return reject("dive ground changed after warning");
        }
        Location arrival = new Location(center.getWorld(), center.getX() + config.arrivalX(), lane.center.getY(),
                center.getZ() + config.arrivalZ());
        if (Math.abs(lane.center.getY() - config.arrivalY()) <= config.arrivalHeight() / 2 + 3
                && lane.contains(arrival, config.arrivalRadius())) {
            return reject("dive overlaps protected arrival");
        }
        // Sample the whole footprint: void, protection or an unusable raised surface is not an arena.
        int lengthSteps = (int) Math.ceil(lane.length);
        int widthSteps = (int) Math.ceil(lane.width);
        for (int x = 0; x <= lengthSteps; x++) {
            double along = -lane.length / 2 + x * lane.length / lengthSteps;
            for (int z = 0; z <= widthSteps; z++) {
                double across = -lane.width / 2 + z * lane.width / widthSteps;
                if (lane.surface == null && !walkable(lane.point(along, across))) {
                    return false;
                }
            }
        }
        double search = lane.length + config.laneWarningSeconds() * 3 + 32;
        var clouds = nearbyBreath(lane.center, search, lane.surface == null ? 6 : 16);
        if (clouds.size() > 32 || clouds.stream().anyMatch(cloud -> !Float.isFinite(cloud.getRadius())
                || cloud.getRadius() > 32 || lane.contains(cloud.getLocation(), cloud.getRadius() + 1))
                || existing.stream().anyMatch(other -> rectanglesOverlap(lane, other))) {
            return reject("dive overlaps a breath cloud or another lane");
        }
        // Do not add pressure while somebody is already escaping existing danger.
        if (participants.stream()
                .anyMatch(player -> existing.stream().anyMatch(other -> other.contains(player.getLocation(), 1))
                        || clouds.stream().anyMatch(cloud -> overlaps(player.getLocation(), 0.5, cloud)))) {
            return reject("participant is still escaping existing breath");
        }
        boolean escapes = participants.stream().filter(player -> lane.contains(player.getLocation(), 1))
                .allMatch(player -> lane.surface == null ? laneEscape(player.getLocation(), lane, existing, clouds)
                        : terrainEscape(player.getLocation(), step -> lane.contains(step, 1), existing, List.of(),
                                clouds, config.laneWarningSeconds()));
        return escapes || reject("dive has no body-sized escape within warning time");
    }

    boolean areaSafe(HuntArea area, List<HuntArea> existing, List<BreathLane> lanes, List<Player> participants,
            double warningSeconds) {
        if (area.surface != null && !area.surface.unchanged()) {
            return reject("area ground changed after warning");
        }
        Location arrival = new Location(center.getWorld(), center.getX() + config.arrivalX(), area.center.getY(),
                center.getZ() + config.arrivalZ());
        if (Math.abs(area.center.getY() - config.arrivalY()) <= config.arrivalHeight() / 2 + 3
                && area.contains(arrival, config.arrivalRadius())) {
            return reject("area overlaps protected arrival");
        }
        double search = area.radius + warningSeconds * 3 + 32;
        var clouds = nearbyBreath(area.center, search, area.surface == null ? 6 : 16);
        if (clouds.size() > 32 || clouds.stream().anyMatch(cloud -> !Float.isFinite(cloud.getRadius())
                || cloud.getRadius() > 32 || overlaps(area.center, area.radius + 1, cloud))) {
            return reject("area overlaps native breath or has too many nearby clouds");
        }
        for (double x = -area.radius; x <= area.radius; x += 0.5) {
            for (double z = -area.radius; z <= area.radius; z += 0.5) {
                Location spot = area.center.clone().add(x, 0, z);
                if (area.surface != null) {
                    Location projected = area.surface.project(spot);
                    if (projected == null) {
                        continue;
                    }
                    spot = projected;
                }
                final Location checked = spot;
                if (area.contains(spot, 0) && (area.surface == null && !walkable(spot)
                        || existing.stream().anyMatch(other -> other.contains(checked, 1))
                        || lanes.stream().anyMatch(lane -> lane.contains(checked, 1)))) {
                    return reject("area overlaps another hazard or has unusable ground");
                }
            }
        }
        boolean escapes =
                participants.stream().filter(player -> area.contains(player.getLocation(), 1)).allMatch(player -> {
                    if (area.surface != null) {
                        return terrainEscape(player.getLocation(), step -> area.contains(step, 1), lanes, existing,
                                clouds, warningSeconds);
                    }
                    Location start = groundBelow(player.getLocation());
                    if (start == null) {
                        return false;
                    }
                    for (int direction = 0; direction < 16; direction++) {
                        double angle = direction * Math.PI * 2 / 16;
                        for (double distance = 0; distance <= warningSeconds * 3; distance += 0.5) {
                            Location step = start.clone().add(Math.cos(angle) * distance, 0,
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
        return escapes || reject("area has no body-sized escape within warning time");
    }

    /** Owned pools may overlap; their combined danger still needs a body-sized walking escape. */
    boolean poolsSafe(List<HuntArea> pools, List<BreathLane> lanes, List<Player> participants, double warningSeconds) {
        if (!pools.stream().allMatch(pool -> areaSafe(pool, List.of(), lanes, participants, warningSeconds))) {
            return false;
        }
        return participants.stream()
                .filter(player -> pools.stream().anyMatch(pool -> pool.contains(player.getLocation(), 1)))
                .allMatch(player -> terrainEscape(player.getLocation(),
                        step -> pools.stream().anyMatch(pool -> pool.contains(step, 1)), lanes, List.of(),
                        List.of(), warningSeconds));
    }

    /** Resolve feet to nearby footing, including a player jumping when a mark expires. */
    Location groundBelow(Location feet) {
        if (config.movementAttacks()) {
            Location ground = AttackSurface.column(feet, 0, 4);
            return ground != null && contains(ground) && !protectedArrival(ground) ? ground : null;
        }
        Location ground = feet.clone();
        for (int y = feet.getBlockY(); y >= feet.getBlockY() - 3; y--) {
            ground.setY(y);
            if (walkable(ground)) {
                return ground.clone();
            }
        }
        return null;
    }

    boolean surface(BreathLane lane) {
        lane.surface = AttackSurface.capture(lane.center, (lane.eastWest ? lane.length : lane.width) / 2,
                (lane.eastWest ? lane.width : lane.length) / 2, point -> lane.contains(point, 1), this);
        return lane.surface != null;
    }

    boolean surface(HuntArea area) {
        area.surface = AttackSurface.capture(area.center, area.radius, area.radius,
                point -> area.contains(point, 1), this);
        return area.surface != null;
    }

    private boolean terrainEscape(Location feet, java.util.function.Predicate<Location> inside,
            List<BreathLane> lanes, List<HuntArea> areas, List<AreaEffectCloud> clouds, double seconds) {
        Location start = groundBelow(feet);
        if (start == null) {
            return false;
        }
        for (int direction = 0; direction < 16; direction++) {
            double angle = direction * Math.PI * 2 / 16;
            Location previous = start;
            double cost = 0;
            for (double distance = 0; distance <= seconds * 3; distance += 0.5) {
                Location next = start.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                next.setY(previous.getY());
                Location step = AttackSurface.step(next);
                if (step == null || !contains(step) || protectedArrival(step)
                        || lanes.stream().anyMatch(lane -> lane.contains(step, 0.5))
                        || areas.stream().anyMatch(area -> area.contains(step, 0.5))
                        || clouds.stream().anyMatch(cloud -> overlaps(step, 0.5, cloud))) {
                    break;
                }
                cost += 0.5 + Math.max(0, step.getY() - previous.getY()) * 0.5;
                if (cost > seconds * 3) {
                    break;
                }
                if (!inside.test(step)) {
                    return true;
                }
                previous = step;
            }
        }
        return false;
    }

    boolean landingSafe() {
        // Native landing uses the real podium. Require ground-level melee approaches, not a void perch.
        Location podium = landingPodium();
        int distance = (int) Math.min(8, config.landingRadius() - 3);
        for (int[] offset : List.of(new int[] {distance, 0}, new int[] {0, distance}, new int[] {-distance, 0},
                new int[] {0, -distance})) {
            Location column = podium.clone().add(offset[0] + 0.5, 0, offset[1] + 0.5);
            column.setY(center.getWorld().getHighestBlockYAt(column.getBlockX(), column.getBlockZ()) + 1);
            Location approach = groundBelow(column);
            if (approach == null || Math.sqrt(horizontalSquared(approach, center.getX(), center.getZ())) + 3 > config
                    .landingRadius() || Math.abs(approach.getY() - podium.getY()) > 8) {
                return false;
            }
        }
        double reach = config.landingRadius() + 32;
        var clouds = nearbyBreath(podium, reach, 32);
        return clouds.size() <= 32 && clouds.stream().noneMatch(cloud -> !Float.isFinite(cloud.getRadius())
                || cloud.getRadius() > 32 || horizontalSquared(cloud.getLocation(), center.getX(),
                        center.getZ()) <= Math.pow(config.landingRadius() + cloud.getRadius(), 2));
    }

    private List<AreaEffectCloud> nearbyBreath(Location location, double horizontal, double vertical) {
        return location.getWorld().getNearbyEntities(location, horizontal, vertical, horizontal).stream()
                .filter(entity -> entity instanceof AreaEffectCloud cloud && cloud.isValid()
                        && cloud.getParticle() == Particle.DRAGON_BREATH)
                .map(entity -> (AreaEffectCloud) entity).limit(33).toList();
    }

    boolean atPodium(Location dragon) {
        return contains(dragon) && horizontalSquared(dragon, center.getX(), center.getZ()) <= 64
                && Math.abs(dragon.getY() - landingPodium().getY()) <= 8;
    }

    private Location landingPodium() {
        // Paper's getPodium() is the fight origin (normally Y=0), not the actual portal surface.
        Location podium = center.clone();
        int highest = center.getWorld().getHighestBlockYAt(center.getBlockX(), center.getBlockZ());
        if (!center.getWorld().getBlockAt(center.getBlockX(), highest, center.getBlockZ()).isEmpty()) {
            podium.setY(highest + 1);
        }
        return podium;
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
