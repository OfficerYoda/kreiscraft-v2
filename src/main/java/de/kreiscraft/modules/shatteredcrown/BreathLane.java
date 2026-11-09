package de.kreiscraft.modules.shatteredcrown;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

/** Immutable warned footprint; the same rectangle renders and damages, never follows a target. */
final class BreathLane {
    final Location center;
    final double length;
    final double width;
    final boolean eastWest;
    TextDisplay indicator;
    double age;
    boolean active;

    BreathLane(Location center, double length, double width, boolean eastWest) {
        this.center = center.clone();
        this.length = length;
        this.width = width;
        this.eastWest = eastWest;
    }

    boolean contains(Location location, double margin) {
        return center.getWorld().equals(location.getWorld()) && Math.abs(location.getY() - center.getY()) <= 3
                && Math.abs(location.getX() - center.getX()) <= (eastWest ? length : width) / 2 + margin
                && Math.abs(location.getZ() - center.getZ()) <= (eastWest ? width : length) / 2 + margin;
    }

    Location point(double along, double across) {
        return center.clone().add(eastWest ? along : across, 0, eastWest ? across : along);
    }
}
