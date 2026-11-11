package de.kreiscraft.modules.shatteredcrown;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** The encounter's payload-free and dragon-breath visual effects. */
public final class CrownParticles {
    private CrownParticles() {}

    public static void spawn(Player player, Particle particle, Location location, int count,
            double x, double y, double z, double speed) {
        Float data = particle == Particle.DRAGON_BREATH ? 1.0f : null;
        player.spawnParticle(particle, location, count, x, y, z, speed, data);
    }
}
