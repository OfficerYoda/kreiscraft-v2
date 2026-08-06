package de.kreiscraft.modules.jail;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Persists jailed-player state to {@code data/jail.yml}.
 *
 * <p>Manual serialization is used rather than {@code node.get(JailEntry.class)} because
 * {@link Instant} has no registered Configurate serializer. {@code Location} is serialized via the
 * registered {@code LocationSerializer}; {@code Instant} is stored as epoch-millis.
 */
public final class JailData extends DataFile<Map<UUID, JailEntry>> {

    public JailData(Path dataFolder) {
        super(dataFolder, "data/jail.yml");
    }

    @Override
    protected Map<UUID, JailEntry> load(ConfigurationNode node) throws SerializationException {
        Map<UUID, JailEntry> map = new HashMap<>();
        for (var uuidEntry : node.childrenMap().entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(String.valueOf(uuidEntry.getKey()));
            } catch (IllegalArgumentException e) {
                continue; // skip malformed keys
            }
            ConfigurationNode entryNode = uuidEntry.getValue();
            Location restoreLocation = entryNode.node("restore-location").get(Location.class);
            String reason = entryNode.node("reason").getString("");
            long epochMillis = entryNode.node("jailed-at").getLong(Instant.now().toEpochMilli());
            map.put(uuid, new JailEntry(restoreLocation, reason, Instant.ofEpochMilli(epochMillis)));
        }
        return map;
    }

    @Override
    protected void save(Map<UUID, JailEntry> state, ConfigurationNode node)
            throws SerializationException {
        node.set(null);
        for (var entry : state.entrySet()) {
            ConfigurationNode entryNode = node.node(entry.getKey().toString());
            JailEntry je = entry.getValue();
            entryNode.node("restore-location").set(Location.class, je.restoreLocation());
            entryNode.node("reason").set(je.reason());
            entryNode.node("jailed-at").set(je.jailedAt().toEpochMilli());
        }
    }

    @Override
    protected Map<UUID, JailEntry> empty() {
        return new HashMap<>();
    }
}
