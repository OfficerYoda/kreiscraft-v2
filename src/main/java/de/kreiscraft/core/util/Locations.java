package de.kreiscraft.core.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;

public final class Locations {

    private Locations() {}

    public static Map<String, Object> toMap(Location loc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", loc.getWorld() == null ? null : loc.getWorld().getName());
        map.put("x", loc.getX());
        map.put("y", loc.getY());
        map.put("z", loc.getZ());
        map.put("yaw", (double) loc.getYaw());
        map.put("pitch", (double) loc.getPitch());
        return map;
    }

    public static Location fromMap(Map<String, Object> map, Function<String, World> worldLookup) {
        String worldName = (String) map.get("world");
        World world = worldName == null ? null : worldLookup.apply(worldName);
        double x = ((Number) map.get("x")).doubleValue();
        double y = ((Number) map.get("y")).doubleValue();
        double z = ((Number) map.get("z")).doubleValue();
        float yaw = map.containsKey("yaw") ? ((Number) map.get("yaw")).floatValue() : 0f;
        float pitch = map.containsKey("pitch") ? ((Number) map.get("pitch")).floatValue() : 0f;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static double distanceSquaredIgnoringWorld(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
