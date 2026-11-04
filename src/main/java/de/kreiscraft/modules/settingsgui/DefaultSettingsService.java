package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.UUID;
import org.bukkit.Particle;

/**
 * GUI-facing client over the always-available {@link PlayerPreferencesService}.
 *
 * <p>The settings GUI owns no persistence of its own: every read and write is delegated to the
 * shared preferences boundary, so preferences persist whether or not this module is enabled. Reads
 * fall back to the owning module's default when the player has no stored value, so the GUI shows the
 * same value the feature would actually use.
 *
 * <p>A click is applied directly to the preferences boundary. There is deliberately no time-based
 * debounce: InvUI dispatches exactly one click to exactly one item, so one physical click produces
 * exactly one logical mutation.
 */
public final class DefaultSettingsService implements SettingsService {

    /** Click-through is opt-out, matching the click-through module default. */
    static final boolean CLICK_THROUGH_DEFAULT_ENABLED = true;

    /** Fallback shown in the GUI when a player has no stored combat-display preference. */
    static final CombatDisplay DISPLAY_FALLBACK = CombatDisplay.ACTIONBAR;

    /** Effects are opt-out once the effects module is enabled. */
    static final boolean EFFECTS_DEFAULT_ENABLED = true;

    private final PlayerPreferencesService preferences;
    private final PlayerLanguage defaultLanguage;
    private final Particle defaultEffectsParticle;

    public DefaultSettingsService(
            PlayerPreferencesService preferences,
            PlayerLanguage defaultLanguage,
            Particle defaultEffectsParticle) {
        this.preferences = preferences;
        this.defaultLanguage = defaultLanguage;
        this.defaultEffectsParticle = defaultEffectsParticle;
    }

    @Override
    public PlayerLanguage language(UUID playerId) {
        return preferences.preferences(playerId).language().orElse(defaultLanguage);
    }

    @Override
    public void setLanguage(UUID playerId, PlayerLanguage language) {
        preferences.setLanguage(playerId, language);
    }

    @Override
    public boolean clickThrough(UUID playerId) {
        return preferences.clickThrough(playerId, CLICK_THROUGH_DEFAULT_ENABLED);
    }

    @Override
    public void setClickThrough(UUID playerId, boolean enabled) {
        preferences.setClickThrough(playerId, enabled);
    }

    @Override
    public CombatDisplay combatDisplay(UUID playerId) {
        return preferences.combatDisplay(playerId, DISPLAY_FALLBACK);
    }

    @Override
    public void setCombatDisplay(UUID playerId, CombatDisplay display) {
        preferences.setCombatDisplay(playerId, display);
    }

    @Override
    public boolean effectsEnabled(UUID playerId) {
        return preferences
                .preferences(playerId)
                .effectsEnabled()
                .orElse(EFFECTS_DEFAULT_ENABLED);
    }

    @Override
    public void setEffectsEnabled(UUID playerId, boolean enabled) {
        preferences.setEffectsEnabled(playerId, enabled);
    }

    @Override
    public Particle effectsParticle(UUID playerId) {
        return preferences
                .preferences(playerId)
                .effectsParticle()
                .orElse(defaultEffectsParticle);
    }

    @Override
    public void setEffectsParticle(UUID playerId, Particle particle) {
        preferences.setEffectsParticle(playerId, particle);
    }
}
