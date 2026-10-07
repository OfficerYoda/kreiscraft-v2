package de.kreiscraft.modules.effects;

import java.util.UUID;
import org.bukkit.Particle;

/**
 * Published service for querying and mutating per-player particle-trail state.
 *
 * <p>State is persisted through the always-available player-preferences boundary, so a toggle or
 * particle change made here survives reconnects and is visible to the Player Settings GUI.
 */
public interface EffectsService {

    /**
     * Returns {@code true} when particle trails are enabled for the given player. A player with no
     * stored preference uses the module default (enabled while the module is enabled).
     */
    boolean isEnabled(UUID playerId);

    /**
     * Flips particle trails on/off for the given player and persists the new state. Repeating this
     * call returns the player to the state they started from.
     */
    void toggle(UUID playerId);

    /**
     * Returns the particle type selected for the given player, or the configured default if the
     * player has no stored preference.
     */
    Particle getParticle(UUID playerId);

    /** Overrides and persists the particle type for the given player. */
    void setParticle(UUID playerId, Particle particle);
}
