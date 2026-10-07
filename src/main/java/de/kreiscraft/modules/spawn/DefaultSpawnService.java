package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.ServerConfigSchema;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Default implementation of {@link SpawnService}.
 *
 * <p>Per ADR-10 the spawn location and teleport range are operator-owned Server Configuration, so
 * they are read from and written to {@code config.yml} through {@link ConfigService}. Both are
 * read live on every call rather than cached, which means a successful command edit and a
 * {@code /kreiscraft reload} are observed immediately.
 */
public final class DefaultSpawnService implements SpawnService {

    private static final String MODULE = "spawn";
    private static final String LOCATION = "location";
    private static final String TELEPORT_RANGE = "teleport-range";

    private final ConfigService config;
    private SpawnConfig moduleConfig;

    public DefaultSpawnService(ConfigService config, SpawnConfig moduleConfig) {
        this.config = config;
        this.moduleConfig = moduleConfig;
    }

    @Override
    public Optional<Location> spawnLocation() {
        ConfigurationNode node = config.module(MODULE).node(LOCATION);
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(node.get(Location.class));
        } catch (SerializationException e) {
            throw new IllegalStateException("invalid spawn location in Server Configuration", e);
        }
    }

    @Override
    public void setSpawn(Location location) {
        config.applyEdit(ServerConfigSchema.spawnLocation(), location);
    }

    @Override
    public void setSpawn(Location location, int teleportRange) {
        config.applyEdit(ServerConfigSchema.spawnLocation(), location);
        config.applyEdit(ServerConfigSchema.spawnTeleportRange(), teleportRange);
    }

    @Override
    public boolean isInSpawnRadius(Player player) {
        Optional<Location> spawn = spawnLocation();
        if (spawn.isEmpty()) {
            return false;
        }
        Location spawnLoc = spawn.get();
        Location playerLoc = player.getLocation();
        // distance() throws if the two locations are in different worlds.
        if (spawnLoc.getWorld() == null || !spawnLoc.getWorld().equals(playerLoc.getWorld())) {
            return false;
        }
        return spawnLoc.distance(playerLoc) <= teleportRange();
    }

    /** The live teleport range, falling back to the module-config default when the key is absent. */
    private int teleportRange() {
        ConfigurationNode node = config.module(MODULE).node(TELEPORT_RANGE);
        if (node.virtual() || node.isNull()) {
            return moduleConfig.teleportRange();
        }
        return node.getInt(moduleConfig.teleportRange());
    }

    /** Called by {@link SpawnModule#onConfigReload} to apply updated tunables at runtime. */
    public void updateConfig(SpawnConfig config) {
        this.moduleConfig = config;
    }
}
