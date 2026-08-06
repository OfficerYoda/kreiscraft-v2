package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Persists the server spawn {@link Location} to {@code data/spawn.yml}.
 * The state type is {@link AtomicReference}{@code <Location>} so that
 * {@link #update(java.util.function.Consumer)} can mutate it in place.
 * The reference holds {@code null} when no spawn has been set.
 */
public final class SpawnData extends DataFile<AtomicReference<Location>> {

    public SpawnData(Path dataFolder) {
        super(dataFolder, "data/spawn.yml");
    }

    @Override
    protected AtomicReference<Location> load(ConfigurationNode node) throws SerializationException {
        ConfigurationNode locNode = node.node("location");
        // virtual() == true when the key is absent from YAML; skip deserialization.
        if (locNode.virtual() || locNode.isNull()) {
            return new AtomicReference<>(null);
        }
        return new AtomicReference<>(locNode.get(Location.class));
    }

    @Override
    protected void save(AtomicReference<Location> state, ConfigurationNode node)
            throws SerializationException {
        Location loc = state.get();
        if (loc != null) {
            node.node("location").set(Location.class, loc);
        }
    }

    @Override
    protected AtomicReference<Location> empty() {
        return new AtomicReference<>(null);
    }
}
