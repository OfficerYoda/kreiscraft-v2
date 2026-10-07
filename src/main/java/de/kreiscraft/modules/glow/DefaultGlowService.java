package de.kreiscraft.modules.glow;

import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Entity-backed glow implementation.
 *
 * <p>There is deliberately no in-memory mirror of the glow state. The live entity is authoritative
 * for toggling, and the persisted preference is authoritative for what a player wants on join. When
 * the module is disabled, only glows this module is responsible for (the entity is glowing and the
 * player's persisted preference is enabled) are cleared, so glow from unrelated sources is left
 * alone.
 */
public final class DefaultGlowService implements GlowService {

    /** Glow is opt-in until the player enables it. */
    static final boolean GLOW_DEFAULT_ENABLED = false;

    private final Server server;
    private final PlayerPreferencesService preferences;

    public DefaultGlowService(Server server, PlayerPreferencesService preferences) {
        this.server = server;
        this.preferences = preferences;
    }

    @Override
    public boolean isGlowing(UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player != null && player.isGlowing();
    }

    @Override
    public boolean toggle(Player player) {
        boolean newState = !player.isGlowing();
        player.setGlowing(newState);
        preferences.setGlowEnabled(player.getUniqueId(), newState);
        return newState;
    }

    @Override
    public void reconcile(Player player) {
        boolean desired = preferences.preferences(player.getUniqueId())
                .glowEnabled()
                .orElse(GLOW_DEFAULT_ENABLED);
        player.setGlowing(desired);
    }

    /**
     * Clears glow on every online player whose persisted desired state is enabled, so a module
     * disable does not leave the module's own glow behind.
     */
    void disableAll() {
        for (Player player : server.getOnlinePlayers()) {
            boolean desired = preferences.preferences(player.getUniqueId())
                    .glowEnabled()
                    .orElse(GLOW_DEFAULT_ENABLED);
            if (desired && player.isGlowing()) {
                player.setGlowing(false);
            }
        }
    }
}
