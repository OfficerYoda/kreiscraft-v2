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

    /**
     * Mutable state held in memory and flushed to disk by {@link #flushIfDirty()}.
     *
     * <p>Fields are private to prevent direct writes that would bypass
     * {@link DataFile#update(java.util.function.Consumer)} and its dirty-flag tracking. Mutate
     * only inside an {@code update()} lambda; read via the getters.
     */
    public static final class SpawnState {

        private Location location;
        private int teleportRange; // 0 = not persisted; fall back to module-config default

        SpawnState() {}

        SpawnState(Location location, int teleportRange) {
            this.location = location;
            this.teleportRange = teleportRange;
        }

        public Location getLocation() {
            return location;
        }

        public int getTeleportRange() {
            return teleportRange;
        }

        void setLocation(Location location) {
            this.location = location;
        }

        void setTeleportRange(int teleportRange) {
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
            state.setLocation(locNode.get(Location.class));
        }
        state.setTeleportRange(node.node("teleport-range").getInt(0));
        return state;
    }

    @Override
    protected void save(SpawnState state, ConfigurationNode node) throws SerializationException {
        if (state.getLocation() != null) {
            node.node("location").set(Location.class, state.getLocation());
        }
        if (state.getTeleportRange() > 0) {
            node.node("teleport-range").set(state.getTeleportRange());
        }
    }

    @Override
    protected SpawnState empty() {
        return new SpawnState();
    }
}
