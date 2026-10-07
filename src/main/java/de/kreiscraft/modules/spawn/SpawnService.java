package de.kreiscraft.modules.spawn;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Published service interface for spawn-location management and spawn geometry. */
public interface SpawnService {

    /** Returns the configured spawn location, or empty if none has been set. */
    Optional<Location> spawnLocation();

    /** Persists {@code location} as the new server spawn point. */
    void setSpawn(Location location);

    /**
     * Returns {@code true} when a spawn location is set, the player is in the spawn world, and the
     * player's <em>horizontal</em> (XZ) distance from spawn is within the Spawn Radius. This is the
     * Spawn Access Zone used by {@code /spawn}; vertical position never affects the result. The
     * boundary is inclusive.
     */
    boolean isInSpawnAccessZone(Player player);

    /**
     * Returns {@code true} when a spawn location is set, the player is in the spawn world, and the
     * player's full three-dimensional distance from spawn is within the Spawn Radius. This is the
     * Glide Zone; both horizontal and vertical distance count. The boundary is inclusive.
     */
    boolean isInGlideZone(Player player);
}
