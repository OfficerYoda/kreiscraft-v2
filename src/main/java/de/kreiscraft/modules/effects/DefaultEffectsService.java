package de.kreiscraft.modules.effects;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Particle;

/** Thread-safe, in-memory implementation of {@link EffectsService}. State is not persisted. */
public final class DefaultEffectsService implements EffectsService {

    private final ConcurrentHashMap<UUID, EffectsState> states = new ConcurrentHashMap<>();
    private volatile EffectsConfig config;

    public DefaultEffectsService(EffectsConfig config) {
        this.config = config;
    }

    @Override
    public boolean isEnabled(UUID playerId) {
        EffectsState state = states.get(playerId);
        return state != null && state.enabled;
    }

    @Override
    public void toggle(UUID playerId) {
        EffectsState state = states.computeIfAbsent(playerId, k -> new EffectsState(config.defaultParticle()));
        state.enabled = !state.enabled;
    }

    @Override
    public Particle getParticle(UUID playerId) {
        EffectsState state = states.get(playerId);
        return state != null ? state.particle : config.defaultParticle();
    }

    @Override
    public void setParticle(UUID playerId, Particle particle) {
        states.computeIfAbsent(playerId, k -> new EffectsState(config.defaultParticle())).particle = particle;
    }

    /** Returns the active configuration. Used by listeners to read count / offset values. */
    public EffectsConfig getConfig() {
        return config;
    }

    /** Called by {@code EffectsModule.onConfigReload} when the config is refreshed. */
    public void updateConfig(EffectsConfig config) {
        this.config = config;
    }

    /** Removes per-player state on quit to prevent unbounded map growth. */
    public void removePlayer(UUID playerId) {
        states.remove(playerId);
    }
}
