package de.kreiscraft.modules.bedrock;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Persists known Bedrock players to {@code data/bedrock-players.yml}.
 *
 * <p>YAML layout:
 * <pre>
 * &lt;uuidString&gt;:
 *   name: ".BedrockUser"
 *   first-seen: 2026-08-06T12:34:56Z
 * </pre>
 */
public final class BedrockData extends DataFile<Map<UUID, BedrockRecord>> {

    public BedrockData(Path dataFolder) {
        super(dataFolder, "data/bedrock-players.yml");
    }

    @Override
    protected Map<UUID, BedrockRecord> load(ConfigurationNode node) throws SerializationException {
        Map<UUID, BedrockRecord> map = new HashMap<>();
        for (var entry : node.childrenMap().entrySet()) {
            UUID id = UUID.fromString(String.valueOf(entry.getKey()));
            ConfigurationNode child = entry.getValue();
            String name = child.node("name").getString("");
            String firstSeenStr = child.node("first-seen").getString("");
            Instant firstSeen = firstSeenStr.isEmpty() ? Instant.now() : Instant.parse(firstSeenStr);
            map.put(id, new BedrockRecord(name, id, firstSeen));
        }
        return map;
    }

    @Override
    protected void save(Map<UUID, BedrockRecord> state, ConfigurationNode node)
            throws SerializationException {
        node.set(null);
        for (var entry : state.entrySet()) {
            BedrockRecord r = entry.getValue();
            ConfigurationNode child = node.node(entry.getKey().toString());
            child.node("name").set(r.name());
            child.node("first-seen").set(r.firstSeen().toString());
        }
    }

    @Override
    protected Map<UUID, BedrockRecord> empty() {
        return new HashMap<>();
    }
}
