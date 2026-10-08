package de.kreiscraft.modules.positions;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Persists per-player named waypoints to {@code data/positions.yml}.
 *
 * <p>YAML structure:
 *
 * <pre>
 * &lt;uuid&gt;:
 *   &lt;name&gt;:
     *     location: { world: world, x: 0, y: 64, z: 0, yaw: 0, pitch: 0 }
     *     saved-by: &lt;uuid&gt;
     *     saved-by-name: Steve
     *     saved-at: 1791460800000
 * </pre>
 */
public final class PositionsData extends DataFile<Map<UUID, Map<String, PositionEntry>>> {

    public PositionsData(Path dataFolder) {
        super(dataFolder, "data/positions.yml");
    }

    @Override
    protected Map<UUID, Map<String, PositionEntry>> load(ConfigurationNode node)
            throws SerializationException {
        Map<UUID, Map<String, PositionEntry>> map = new LinkedHashMap<>();
        for (var uuidEntry : node.childrenMap().entrySet()) {
            UUID uuid = UUID.fromString(String.valueOf(uuidEntry.getKey()));
            Map<String, PositionEntry> positions = new LinkedHashMap<>();
            for (var nameEntry : uuidEntry.getValue().childrenMap().entrySet()) {
                String name = String.valueOf(nameEntry.getKey());
                ConfigurationNode entryNode = nameEntry.getValue();
                var location = entryNode.node("location").get(org.bukkit.Location.class);
                if (location != null) {
                    String savedByValue = entryNode.node("saved-by").getString();
                    UUID savedBy = savedByValue == null ? null : parseUuid(savedByValue);
                    String savedByName = entryNode.node("saved-by-name").getString();
                    Long savedAtValue = entryNode.node("saved-at").get(Long.class);
                    Instant savedAt = savedAtValue == null ? null : Instant.ofEpochMilli(savedAtValue);
                    positions.put(name, new PositionEntry(location, savedBy, savedByName, savedAt));
                }
            }
            map.put(uuid, positions);
        }
        return map;
    }

    @Override
    protected void save(
            Map<UUID, Map<String, PositionEntry>> state, ConfigurationNode node)
            throws SerializationException {
        node.set(null);
        for (var uuidEntry : state.entrySet()) {
            for (var nameEntry : uuidEntry.getValue().entrySet()) {
                ConfigurationNode entryNode =
                        node.node(uuidEntry.getKey().toString(), nameEntry.getKey());
                PositionEntry entry = nameEntry.getValue();
                entryNode.node("location").set(org.bukkit.Location.class, entry.location());
                if (entry.savedBy() != null) {
                    entryNode.node("saved-by").set(entry.savedBy().toString());
                }
                if (entry.savedByName() != null) {
                    entryNode.node("saved-by-name").set(entry.savedByName());
                }
                if (entry.savedAt() != null) {
                    entryNode.node("saved-at").set(entry.savedAt().toEpochMilli());
                }
            }
        }
    }

    @Override
    protected Map<UUID, Map<String, PositionEntry>> empty() {
        return new LinkedHashMap<>();
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
