package de.kreiscraft.modules.settingsgui;

import java.time.Instant;

/**
 * Per-player settings snapshot held in the in-memory state map.
 *
 * <p>Only {@code clickThrough} and {@code combatDisplay} are persisted to {@code
 * data/settings.yml}. {@code lastClickAt} is a transient in-memory field used for click-through
 * debounce; it is deliberately excluded from serialization in {@link SettingsData} and resets to
 * {@code null} on server restart.
 */
public record Settings(boolean clickThrough, CombatDisplay combatDisplay, Instant lastClickAt) {

    /** Convenience constructor that leaves {@code lastClickAt} null. */
    public Settings(boolean clickThrough, CombatDisplay combatDisplay) {
        this(clickThrough, combatDisplay, null);
    }

    public static Settings defaults() {
        return new Settings(true, CombatDisplay.ACTIONBAR, null);
    }
}
