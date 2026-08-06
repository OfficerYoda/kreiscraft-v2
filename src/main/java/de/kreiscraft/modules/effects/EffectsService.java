package de.kreiscraft.modules.effects;

import java.util.UUID;
import org.bukkit.Particle;

/** Published service for querying and mutating per-player particle-trail state. */
public interface EffectsService {

    /**
     * Returns {@code true} when particle trails are enabled for the given player.
     * Returns {@code false} if the player has never interacted with the effects system.
     */
    boolean isEnabled(UUID playerId);

    /**
     * Toggles particle trails on/off for the given player.
     * A player with no prior state is treated as "off", so the first toggle enables trails.
     */
    void toggle(UUID playerId);

    /**
     * Returns the particle type selected for the given player, or the configured default if
     * the player has no stored preference.
     */
    Particle getParticle(UUID playerId);

    /** Overrides the particle type for the given player. */
    void setParticle(UUID playerId, Particle particle);
}
