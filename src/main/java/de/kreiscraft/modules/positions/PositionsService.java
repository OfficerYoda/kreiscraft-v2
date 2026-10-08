package de.kreiscraft.modules.positions;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/** Service interface for managing per-player named waypoints. */
public interface PositionsService {

    /**
     * Returns an unmodifiable snapshot of all positions for the given player.
     * Returns an empty map if the player has no saved positions.
     */
    Map<String, PositionEntry> forPlayer(UUID uuid);

    /** Looks up a single named position. Returns empty if it doesn't exist. */
    Optional<PositionEntry> get(UUID uuid, String name);

    /**
     * Creates or overwrites the named position for the given player.
     *
     * @param uuid     the player's UUID
     * @param name     the position name (case-sensitive)
     * @param location the location to store; its world must be non-null
     */
    void set(UUID uuid, String name, Location location);

    /** Creates or overwrites a position and records the saving player's identity. */
    default void set(UUID uuid, String name, Location location, UUID savedBy, String savedByName) {
        set(uuid, name, location);
    }

    /**
     * Removes the named position for the given player. Does nothing if it
     * doesn't exist.
     */
    void remove(UUID uuid, String name);
}
