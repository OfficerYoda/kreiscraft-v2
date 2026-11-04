package de.kreiscraft.shared.preferences;

import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.bukkit.Particle;

/** Default {@link PlayerPreferencesService} backed by {@link PlayerPreferencesData}. */
public final class DefaultPlayerPreferencesService implements PlayerPreferencesService {

    private final PlayerPreferencesData data;

    public DefaultPlayerPreferencesService(PlayerPreferencesData data) {
        this.data = data;
    }

    @Override
    public PlayerPreferences preferences(UUID playerId) {
        Map<UUID, PlayerPreferences> map = data.get();
        if (map == null) {
            return PlayerPreferences.empty();
        }
        return map.getOrDefault(playerId, PlayerPreferences.empty());
    }

    @Override
    public void setLanguage(UUID playerId, PlayerLanguage language) {
        update(playerId, prefs -> prefs.withLanguage(language));
    }

    @Override
    public void setClickThrough(UUID playerId, boolean enabled) {
        update(playerId, prefs -> prefs.withClickThrough(enabled));
    }

    @Override
    public void setCombatDisplay(UUID playerId, CombatDisplay display) {
        update(playerId, prefs -> prefs.withCombatDisplay(display));
    }

    @Override
    public void setEffectsEnabled(UUID playerId, boolean enabled) {
        update(playerId, prefs -> prefs.withEffectsEnabled(enabled));
    }

    @Override
    public void setEffectsParticle(UUID playerId, Particle particle) {
        update(playerId, prefs -> prefs.withEffectsParticle(particle));
    }

    private void update(UUID playerId, UnaryOperator<PlayerPreferences> mutator) {
        data.update(map -> map.put(
                playerId, mutator.apply(map.getOrDefault(playerId, PlayerPreferences.empty()))));
    }
}
