package de.kreiscraft.modules.effects;

import java.time.Duration;
import org.bukkit.Particle;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record EffectsConfig(
        Particle defaultParticle,
        Duration schedule,
        int count,
        double offsetX,
        double offsetY,
        double offsetZ,
        boolean force) {

    /** Fallback emission period: 5 Minecraft ticks (5 × 50 ms). */
    public static final Duration DEFAULT_SCHEDULE = Duration.ofMillis(5 * 50L);

    /** Fallback defaults applied when keys are absent from the YAML section. */
    public EffectsConfig {
        if (defaultParticle == null) {
            defaultParticle = Particle.HAPPY_VILLAGER;
        }
        if (schedule == null) {
            schedule = DEFAULT_SCHEDULE;
        }
    }
}
