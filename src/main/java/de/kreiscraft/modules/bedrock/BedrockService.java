package de.kreiscraft.modules.bedrock;

import java.util.Collection;
import java.util.UUID;

/**
 * Service interface for detecting and tracking Bedrock (Geyser/Floodgate) players.
 *
 * <p>Bedrock players are identified by a configurable name prefix (default {@code "."}).
 * Known players are persisted across restarts so admins can whitelist them even when
 * they are offline.
 */
public interface BedrockService {

    /**
     * Returns {@code true} if {@code playerName} starts with the configured prefix,
     * indicating a Bedrock player.
     */
    boolean isBedrock(String playerName);

    /**
     * Records that a Bedrock player has connected. Deduplicates by UUID:
     * if the player is already known the call is a no-op.
     *
     * @param name the player's in-game name (including the Bedrock prefix)
     * @param uuid the player's UUID
     */
    void seen(String name, UUID uuid);

    /** Returns a snapshot of all Bedrock players seen since the data file was created. */
    Collection<BedrockRecord> knownPlayers();
}
