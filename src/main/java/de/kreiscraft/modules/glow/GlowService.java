package de.kreiscraft.modules.glow;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Reads and mutates a player's Bukkit {@code Glowing} state.
 *
 * <p>The entity is the source of truth: a toggle flips whatever the entity currently shows and then
 * persists the resulting value as the player's desired preference. On join, the persisted preference
 * is reconciled back onto the entity.
 */
public interface GlowService {

    /** The entity's current glowing state, or {@code false} when the player is not online. */
    boolean isGlowing(UUID playerId);

    /**
     * Flips {@code player}'s glow based on the entity's current state, persists the resulting desired
     * state, and returns that new state.
     */
    boolean toggle(Player player);

    /** Applies the player's persisted desired glow state onto the joining entity. */
    void reconcile(Player player);
}
