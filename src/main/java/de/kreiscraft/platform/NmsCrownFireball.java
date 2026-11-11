package de.kreiscraft.platform;

import org.bukkit.craftbukkit.entity.CraftFireball;
import org.bukkit.entity.LargeFireball;

/** Constant native descent: balance air drag against acceleration, without per-tick steering. */
public final class NmsCrownFireball {
    private NmsCrownFireball() {}

    public static void prepare(LargeFireball fireball, double speed) {
        // Native air inertia is a float; use the same value rather than a rounded double.
        ((CraftFireball) fireball).getHandle().accelerationPower = speed * (1 / (double) 0.95f - 1);
    }
}
