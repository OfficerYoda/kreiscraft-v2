package de.kreiscraft.modules.glow;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class DefaultGlowService implements GlowService {

    private final Server server;
    private final Map<UUID, Boolean> state = new ConcurrentHashMap<>();

    public DefaultGlowService(Server server) {
        this.server = server;
    }

    @Override
    public boolean isGlowing(UUID playerId) {
        return state.getOrDefault(playerId, false);
    }

    @Override
    public void toggle(UUID playerId) {
        boolean newState = !state.getOrDefault(playerId, false);
        state.put(playerId, newState);
        Player player = server.getPlayer(playerId);
        if (player != null) {
            player.setGlowing(newState);
        }
    }

    /**
     * Removes the player's glow state from the tracking map without modifying the
     * player entity (called on quit when the player is already leaving).
     */
    void removePlayer(UUID playerId) {
        state.remove(playerId);
    }

    /**
     * Clears all glow states; for every tracked UUID that is still online,
     * explicitly sets {@code setGlowing(false)} before removing. Called from
     * {@link GlowModule#disable()}.
     */
    void disableAll() {
        Set<UUID> tracked = Set.copyOf(state.keySet());
        for (UUID id : tracked) {
            Player player = server.getPlayer(id);
            if (player != null) {
                player.setGlowing(false);
            }
        }
        state.clear();
    }
}
