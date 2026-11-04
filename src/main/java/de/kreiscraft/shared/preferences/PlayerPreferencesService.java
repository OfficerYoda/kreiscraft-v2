package de.kreiscraft.shared.preferences;

import java.util.UUID;
import org.bukkit.Particle;

/**
 * Always-available boundary for per-player preferences.
 *
 * <p>This service is published by the composition root rather than by a module, so modules such as
 * combat, click-through, and effects can read and persist preferences independently of the
 * Player Settings GUI module. Callers that need a value to be present apply their own module default
 * via the convenience methods or by inspecting {@link #preferences(UUID)}.
 */
public interface PlayerPreferencesService {

    /** Returns the stored preferences for a player, or an empty snapshot when none are stored. */
    PlayerPreferences preferences(UUID playerId);

    void setLanguage(UUID playerId, PlayerLanguage language);

    void setClickThrough(UUID playerId, boolean enabled);

    void setCombatDisplay(UUID playerId, CombatDisplay display);

    void setEffectsEnabled(UUID playerId, boolean enabled);

    void setEffectsParticle(UUID playerId, Particle particle);

    void setTpsBarEnabled(UUID playerId, boolean enabled);

    /** Returns the stored click-through preference, or {@code fallback} when unavailable. */
    default boolean clickThrough(UUID playerId, boolean fallback) {
        return preferences(playerId).clickThrough().orElse(fallback);
    }

    /** Returns the stored combat display, or {@code fallback} when unavailable. */
    default CombatDisplay combatDisplay(UUID playerId, CombatDisplay fallback) {
        return preferences(playerId).combatDisplay().orElse(fallback);
    }
}
