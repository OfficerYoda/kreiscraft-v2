package de.kreiscraft.modules.positions;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
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
                PositionEntry entry = nameEntry.getValue().get(PositionEntry.class);
                if (entry != null) {
                    positions.put(name, entry);
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
                node.node(uuidEntry.getKey().toString(), nameEntry.getKey())
                        .set(PositionEntry.class, nameEntry.getValue());
            }
        }
    }

    @Override
    protected Map<UUID, Map<String, PositionEntry>> empty() {
        return new LinkedHashMap<>();
    }
}
