package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import java.util.UUID;
import org.bukkit.Particle;

/**
 * Read/write view of the preferences the Player Settings GUI exposes.
 *
 * <p>The GUI owns no persistence: every read and write is delegated to the always-available
 * preferences boundary, so a value changed here persists whether or not the settings module is
 * enabled. Reads apply the owning module's default when the player has no stored value, so the GUI
 * always shows a concrete current value.
 */
public interface SettingsService {

    /** The player's effective language, or the server language when none is stored. */
    PlayerLanguage language(UUID playerId);

    void setLanguage(UUID playerId, PlayerLanguage language);

    /** The player's effective click-through state, defaulting to the module default when absent. */
    boolean clickThrough(UUID playerId);

    void setClickThrough(UUID playerId, boolean enabled);

    /** The player's effective combat display, defaulting to the module default when absent. */
    CombatDisplay combatDisplay(UUID playerId);

    void setCombatDisplay(UUID playerId, CombatDisplay display);

    /** The player's effective effects-enabled state, defaulting to the module default when absent. */
    boolean effectsEnabled(UUID playerId);

    void setEffectsEnabled(UUID playerId, boolean enabled);

    /** The player's effective particle, defaulting to the module default when absent. */
    Particle effectsParticle(UUID playerId);

    /** Selects a particle and enables effects immediately. */
    void setEffectsParticle(UUID playerId, Particle particle);

    /** The player's effective glow state, defaulting to the module default when absent. */
    boolean glowEnabled(UUID playerId);

    void setGlowEnabled(UUID playerId, boolean enabled);
}
