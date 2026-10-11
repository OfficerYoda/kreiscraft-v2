package de.kreiscraft.modules.effects;

import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Particle;

/**
 * Preference-backed {@link EffectsService}.
 *
 * <p>Enabled state and particle choice live in the always-available {@link
 * PlayerPreferencesService}, so they survive reconnects and remain available regardless of whether
 * the Player Settings GUI or any other module is enabled. The configured default particle is only
 * a display fallback: emission remains neutral until the player explicitly selects a particle.
 *
 * <p>The module config is read live through the supplied {@link Supplier} so a reload is observed
 * without any push.
 */
public final class DefaultEffectsService implements EffectsService {

    private final PlayerPreferencesService preferences;
    private final Supplier<EffectsConfig> config;
    private final boolean defaultEnabled;

    public DefaultEffectsService(
            PlayerPreferencesService preferences,
            Supplier<EffectsConfig> config,
            boolean defaultEnabled) {
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
                .orElse(config.get().defaultParticle());
    }

    @Override
    public Optional<Particle> selectedParticle(UUID playerId) {
        return preferences.preferences(playerId).effectsParticle();
    }

    @Override
    public void setParticle(UUID playerId, Particle particle) {
        preferences.setEffectsParticle(playerId, particle);
        preferences.setEffectsEnabled(playerId, true);
    }
}
