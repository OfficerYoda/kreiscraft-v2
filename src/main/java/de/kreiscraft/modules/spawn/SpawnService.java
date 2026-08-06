package de.kreiscraft.modules.spawn;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Published service interface for spawn-location management. */
public interface SpawnService {

    /** Returns the configured spawn location, or empty if none has been set. */
    Optional<Location> spawnLocation();

    /** Persists {@code location} as the new server spawn point. */
    void setSpawn(Location location);

    /**
     * Returns {@code true} when a spawn location is set and the player is
     * within {@code teleportRange} blocks of it (used to gate the /spawn command).
     */
    boolean isInSpawnRadius(Player player);
}
