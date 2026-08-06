package de.kreiscraft.modules.effects;

import org.bukkit.Particle;

/** Per-player runtime state for the effects module. Package-private — not part of the public API. */
final class EffectsState {

    boolean enabled;
    Particle particle;

    EffectsState(Particle defaultParticle) {
        this.enabled = false;
        this.particle = defaultParticle;
    }
}
