package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import org.bukkit.Location;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Persists the server spawn {@link Location} and teleport range to {@code data/spawn.yml}.
 * The teleport range ({@code teleportRange > 0}) overrides the module-config default when set
 * via {@code /setspawn <radius>}.
 */
public final class SpawnData extends DataFile<SpawnData.SpawnState> {

    /** Mutable state held in memory and flushed to disk by {@link #flushIfDirty()}. */
    public static final class SpawnState {

        Location location;
        int teleportRange; // 0 = not persisted; fall back to module-config default

        SpawnState() {}

        SpawnState(Location location, int teleportRange) {
            this.location = location;
            this.teleportRange = teleportRange;
        }
    }

    public SpawnData(Path dataFolder) {
        super(dataFolder, "data/spawn.yml");
    }

    @Override
    protected SpawnState load(ConfigurationNode node) throws SerializationException {
        SpawnState state = new SpawnState();
        ConfigurationNode locNode = node.node("location");
        if (!locNode.virtual() && !locNode.isNull()) {
            state.location = locNode.get(Location.class);
        }
        state.teleportRange = node.node("teleport-range").getInt(0);
        return state;
    }

    @Override
    protected void save(SpawnState state, ConfigurationNode node) throws SerializationException {
        if (state.location != null) {
            node.node("location").set(Location.class, state.location);
        }
        if (state.teleportRange > 0) {
            node.node("teleport-range").set(state.teleportRange);
        }
    }

    @Override
    protected SpawnState empty() {
        return new SpawnState();
    }
}
