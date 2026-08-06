package de.kreiscraft.modules.spawn;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Default implementation of {@link SpawnService}. */
public final class DefaultSpawnService implements SpawnService {

    private final SpawnData data;
    private SpawnConfig config;

    public DefaultSpawnService(SpawnData data, SpawnConfig config) {
        this.data = data;
        this.config = config;
    }

    @Override
    public Optional<Location> spawnLocation() {
        return Optional.ofNullable(data.get().get());
    }

    @Override
    public void setSpawn(Location location) {
        data.update(ref -> ref.set(location));
    }

    @Override
    public boolean isInSpawnRadius(Player player) {
        Optional<Location> spawn = spawnLocation();
        if (spawn.isEmpty())
            return false;
        Location spawnLoc = spawn.get();
        Location playerLoc = player.getLocation();
        // distance() throws if the two locations are in different worlds.
        if (spawnLoc.getWorld() == null || !spawnLoc.getWorld().equals(playerLoc.getWorld())) {
            return false;
        }
        return spawnLoc.distance(playerLoc) <= config.teleportRange();
    }

    /** Called by {@link SpawnModule#onConfigReload} to apply updated tunables at runtime. */
    public void updateConfig(SpawnConfig config) {
        this.config = config;
    }
}
