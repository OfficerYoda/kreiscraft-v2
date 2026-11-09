package de.kreiscraft.modules.shatteredcrown;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

/** One fixed circle or sector; rendering and damage share the footprint. */
final class HuntArea {
    final Location center;
    final double radius;
    final double direction;
    final double halfAngle;
    TextDisplay indicator;
    double age;

    HuntArea(Location center, double radius) {
        this(center, radius, 0, 360);
    }

    HuntArea(Location center, double radius, double yaw, double angle) {
        this.center = center.clone();
        this.center.setY(center.getBlockY());
        this.radius = radius;
        this.direction = Math.toRadians(yaw + 90);
        this.halfAngle = Math.toRadians(angle / 2);
    }

    boolean contains(Location location, double margin) {
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        return center.getWorld().equals(location.getWorld()) && Math.abs(location.getY() - center.getY()) <= 3
                && dx * dx + dz * dz <= Math.pow(radius + margin, 2) + 1e-9
                && (halfAngle == Math.PI || Math.hypot(dx, dz) <= margin
                        || Math.abs(Math.atan2(Math.sin(Math.atan2(dz, dx) - direction),
                                Math.cos(Math.atan2(dz, dx) - direction))) <= halfAngle
                                        + Math.asin(Math.min(1, margin / Math.hypot(dx, dz))) + 1e-9);
    }

    Location point(double angle, double distance) {
        return center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
    }
}
