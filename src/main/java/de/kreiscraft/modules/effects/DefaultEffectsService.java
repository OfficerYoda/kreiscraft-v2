package de.kreiscraft.modules.effects;

import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.UUID;
import org.bukkit.Particle;

/**
 * Preference-backed {@link EffectsService}.
 *
 * <p>Enabled state and particle choice live in the always-available {@link
 * PlayerPreferencesService}, so they survive reconnects and remain available regardless of whether
 * the Player Settings GUI or any other module is enabled. A player who has never chosen keeps the
 * module default passed at construction time (enabled when the module is enabled) and the
 * configured default particle; these are never written until the player makes an explicit choice.
 */
public final class DefaultEffectsService implements EffectsService {

    private final PlayerPreferencesService preferences;
    private final boolean defaultEnabled;
    private volatile EffectsConfig config;

    public DefaultEffectsService(
            PlayerPreferencesService preferences, EffectsConfig config, boolean defaultEnabled) {
        this.preferences = preferences;
        this.config = config;
        this.defaultEnabled = defaultEnabled;
    }

    @Override
    public boolean isEnabled(UUID playerId) {
        return preferences.preferences(playerId).effectsEnabled().orElse(defaultEnabled);
    }

    @Override
    public void toggle(UUID playerId) {
        preferences.setEffectsEnabled(playerId, !isEnabled(playerId));
    }

    @Override
    public Particle getParticle(UUID playerId) {
        return preferences
                .preferences(playerId)
                .effectsParticle()
                .orElse(config.defaultParticle());
    }

    @Override
    public void setParticle(UUID playerId, Particle particle) {
        preferences.setEffectsParticle(playerId, particle);
    }

    /** Returns the active configuration. Used by the emitter to read count / offset values. */
    public EffectsConfig getConfig() {
        return config;
    }

    /** Called by {@code EffectsModule.onConfigReload} when the config is refreshed. */
    public void updateConfig(EffectsConfig config) {
        this.config = config;
    }
}
