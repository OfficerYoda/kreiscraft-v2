package de.kreiscraft.shared.preferences;

import java.util.Optional;
import org.bukkit.Particle;

/**
 * An immutable snapshot of one player's persisted preferences.
 *
 * <p>Every component is an {@link Optional} so an absent value is distinguishable from a stored
 * default. Modules consult this snapshot and apply their own module default with {@code orElse(...)}
 * when a preference is unavailable.
 */
public record PlayerPreferences(
        Optional<PlayerLanguage> language,
        Optional<Boolean> clickThrough,
        Optional<CombatDisplay> combatDisplay,
        Optional<Boolean> effectsEnabled,
        Optional<Particle> effectsParticle) {

    /** Normalises null components to {@link Optional#empty()} so snapshots are always non-null. */
    public PlayerPreferences {
        if (language == null) {
            language = Optional.empty();
        }
        if (clickThrough == null) {
            clickThrough = Optional.empty();
        }
        if (combatDisplay == null) {
            combatDisplay = Optional.empty();
        }
        if (effectsEnabled == null) {
            effectsEnabled = Optional.empty();
        }
        if (effectsParticle == null) {
            effectsParticle = Optional.empty();
        }
    }

    /** A snapshot with no preference set. */
    public static PlayerPreferences empty() {
        return new PlayerPreferences(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public PlayerPreferences withLanguage(PlayerLanguage value) {
        return new PlayerPreferences(
                Optional.ofNullable(value), clickThrough, combatDisplay, effectsEnabled,
                effectsParticle);
    }

    public PlayerPreferences withClickThrough(boolean value) {
        return new PlayerPreferences(
                language, Optional.of(value), combatDisplay, effectsEnabled, effectsParticle);
    }

    public PlayerPreferences withCombatDisplay(CombatDisplay value) {
        return new PlayerPreferences(
                language, clickThrough, Optional.ofNullable(value), effectsEnabled,
                effectsParticle);
    }

    public PlayerPreferences withEffectsEnabled(boolean value) {
        return new PlayerPreferences(
                language, clickThrough, combatDisplay, Optional.of(value), effectsParticle);
    }

    public PlayerPreferences withEffectsParticle(Particle value) {
        return new PlayerPreferences(
                language, clickThrough, combatDisplay, effectsEnabled, Optional.ofNullable(value));
    }
}
