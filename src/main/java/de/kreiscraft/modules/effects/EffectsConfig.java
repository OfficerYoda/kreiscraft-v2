package de.kreiscraft.modules.effects;

import org.bukkit.Particle;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record EffectsConfig(Particle defaultParticle, int count, double offsetX, double offsetY, double offsetZ,
        boolean force) {

    /** Fallback defaults applied when keys are absent from the YAML section. */
    public EffectsConfig {
        if (defaultParticle == null) {
            defaultParticle = Particle.HAPPY_VILLAGER;
        }
    }
}
