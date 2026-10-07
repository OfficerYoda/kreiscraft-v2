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
 * <p>Per ADR-10 the spawn location is operator-owned Server Configuration, so it is read from and
 * written to {@code config.yml} through {@link ConfigService}. The location is read live on every
 * call rather than cached, so a successful command edit and a {@code /kreiscraft reload} are
 * observed immediately.
 *
 * <p>The single Spawn Radius lives in the spawn module configuration; both the Spawn Access Zone
 * (XZ) and the Glide Zone (3D) are measured against it.
 */
public final class DefaultSpawnService implements SpawnService {

    private static final String MODULE = "spawn";
    private static final String LOCATION = "location";

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
    public boolean isInSpawnAccessZone(Player player) {
        Location spawnLoc = spawnInPlayerWorld(player);
        if (spawnLoc == null) {
            return false;
        }
        Location playerLoc = player.getLocation();
        double dx = spawnLoc.getX() - playerLoc.getX();
        double dz = spawnLoc.getZ() - playerLoc.getZ();
        return Math.hypot(dx, dz) <= spawnRadius();
    }

    @Override
    public boolean isInGlideZone(Player player) {
        Location spawnLoc = spawnInPlayerWorld(player);
        if (spawnLoc == null) {
            return false;
        }
        return spawnLoc.distance(player.getLocation()) <= spawnRadius();
    }

    /**
     * Returns the configured spawn location when it shares the player's world, otherwise
     * {@code null}. {@code Location#distance} throws when the two locations are in different
     * worlds, so world equality is checked first.
     */
    private Location spawnInPlayerWorld(Player player) {
        Optional<Location> spawn = spawnLocation();
        if (spawn.isEmpty()) {
            return null;
        }
        Location spawnLoc = spawn.get();
        Location playerLoc = player.getLocation();
        if (spawnLoc.getWorld() == null || !spawnLoc.getWorld().equals(playerLoc.getWorld())) {
            return null;
        }
        return spawnLoc;
    }

    /** The configured Spawn Radius. */
    private int spawnRadius() {
        return moduleConfig.spawnRadius();
    }

    /** Called by {@link SpawnModule#onConfigReload} to apply updated tunables at runtime. */
    public void updateConfig(SpawnConfig config) {
        this.moduleConfig = config;
    }
}
