package de.kreiscraft.modules.shatteredcrown;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.entity.Player;

/** Native encounter visuals and their required particle payloads. */
public final class CrownParticles {
    private static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(170, 40, 220), 1);

    private CrownParticles() {}

    public static void spawn(Player player, Particle particle, Location location, int count,
            double x, double y, double z, double speed) {
        Object data = switch (particle) {
            case DRAGON_BREATH -> 1.0f;
            case DUST -> PURPLE;
            default -> null;
        };
        player.spawnParticle(particle, location, count, x, y, z, speed, data);
    }
}
