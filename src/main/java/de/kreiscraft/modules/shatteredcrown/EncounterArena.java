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
        if (!contains(impact) || AttackSurface.column(impact, 0, 4) == null) {
            return false;
        }
        double search = radius + config.bombardmentWarningSeconds() * 3 + 32;
        var danger = nearbyBreath(impact, search, 6);
        if (danger.size() > 32 || danger.stream().anyMatch(cloud -> !Float.isFinite(cloud.getRadius())
                || cloud.getRadius() > 32 || overlaps(impact, radius, cloud))) {
            return false;
        }
        return true;
    }

    private boolean overlaps(Location location, double radius, AreaEffectCloud cloud) {
        Location pool = cloud.getLocation();
        double distance = radius + cloud.getRadius();
        return Math.abs(location.getY() - pool.getY()) <= 3
                && horizontalSquared(location, pool.getX(), pool.getZ()) <= distance * distance;
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
        // Sample the whole footprint: void, protection or an unusable raised surface is not an arena.
        int lengthSteps = (int) Math.ceil(lane.length);
        int widthSteps = (int) Math.ceil(lane.width);
        for (int x = 0; x <= lengthSteps; x++) {
            double along = -lane.length / 2 + x * lane.length / lengthSteps;
            for (int z = 0; z <= widthSteps; z++) {
                double across = -lane.width / 2 + z * lane.width / widthSteps;
                if (!contains(lane.point(along, across))) {
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
        return true;
    }

    boolean areaSafe(HuntArea area, List<HuntArea> existing, List<BreathLane> lanes, List<Player> participants,
            double warningSeconds) {
        if (area.surface != null && !area.surface.unchanged()) {
            return reject("area ground changed after warning");
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
                if (area.contains(spot, 0) && (!contains(spot)
                        || existing.stream().anyMatch(other -> other.contains(checked, 1))
                        || lanes.stream().anyMatch(lane -> lane.contains(checked, 1)))) {
                    return reject("area overlaps another hazard or leaves the combat area");
                }
            }
        }
        return true;
    }

    /** Owned pools may overlap. Permanent destruction intentionally removes walking-route guarantees. */
    boolean poolsSafe(List<HuntArea> pools, List<BreathLane> lanes, List<Player> participants, double warningSeconds) {
        if (!pools.stream().allMatch(pool -> areaSafe(pool, List.of(), lanes, participants, warningSeconds))) {
            return false;
        }
        return true;
    }

    /** Resolve feet to nearby footing, including a player jumping when a mark expires. */
    Location groundBelow(Location feet) {
        Location ground = AttackSurface.column(feet, 0, config.movementAttacks() ? 4 : 3);
        return ground != null && contains(ground) && !protectedArrival(ground) ? ground : null;
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

    boolean landingSafe() {
        // Real podium footing remains valid; cratered walking approaches are not a route veto (#161).
        Location podium = anchorSite(center); // Paper reports a fight origin, not portal surface height.
        if (groundBelow(podium) == null) {
            return false;
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
                && Math.abs(dragon.getY() - anchorSite(center).getY()) <= 8;
    }

    private boolean rectanglesOverlap(BreathLane lane, BreathLane other) {
        return Math.abs(lane.center.getY() - other.center.getY()) <= 6
                && Math.abs(lane.center.getX() - other.center.getX()) <= (lane.eastWest ? lane.length : lane.width) / 2
                        + (other.eastWest ? other.length : other.width) / 2 + 1
                && Math.abs(lane.center.getZ() - other.center.getZ()) <= (lane.eastWest ? lane.width : lane.length) / 2
                        + (other.eastWest ? other.width : other.length) / 2 + 1;
    }

    private static double horizontalSquared(Location location, double x, double z) {
        double dx = location.getX() - x;
        double dz = location.getZ() - z;
        return dx * dx + dz * dz;
    }
}
