package de.kreiscraft.shared.preferences;

/**
 * The UI element used to display combat status to a player.
 *
 * <p>This enum lives in the always-available shared preferences layer so that the combat module can
 * read a persisted preference without depending on the settings GUI module.
 */
public enum CombatDisplay {
    CHAT, ACTIONBAR, TITLE
}
