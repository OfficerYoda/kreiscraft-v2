package de.kreiscraft.modules.spawn;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Published service interface for spawn-location management. */
public interface SpawnService {

    /** Returns the configured spawn location, or empty if none has been set. */
    Optional<Location> spawnLocation();

    /** Persists {@code location} as the new server spawn point (range unchanged). */
    void setSpawn(Location location);

    /**
     * Persists {@code location} as the new server spawn point and stores {@code teleportRange}
     * as the maximum distance from which players may use {@code /spawn}.
     */
    void setSpawn(Location location, int teleportRange);

    /**
     * Returns {@code true} when a spawn location is set and the player is within the effective
     * teleport range (persisted value if set, otherwise the module-config default).
     */
    boolean isInSpawnRadius(Player player);
}
