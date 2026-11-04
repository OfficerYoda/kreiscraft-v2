package de.kreiscraft.testsupport;

import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import de.kreiscraft.shared.preferences.PlayerPreferences;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.bukkit.Particle;

/**
 * In-memory {@link PlayerPreferencesService} for tests that need the real module-default fallback
 * behaviour of the default methods without touching a data file.
 */
public final class InMemoryPlayerPreferencesService implements PlayerPreferencesService {

    private final Map<UUID, PlayerPreferences> store = new HashMap<>();

    @Override
    public PlayerPreferences preferences(UUID playerId) {
        return store.getOrDefault(playerId, PlayerPreferences.empty());
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

    @Override
    public void setTpsBarEnabled(UUID playerId, boolean enabled) {
        update(playerId, prefs -> prefs.withTpsBarEnabled(enabled));
    }

    private void update(UUID playerId, UnaryOperator<PlayerPreferences> mutator) {
        store.put(playerId, mutator.apply(preferences(playerId)));
    }
}
