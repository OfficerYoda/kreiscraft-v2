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
 * Persists Pending Bedrock Authorizations to {@code data/bedrock-authorizations.yml}.
 *
 * <p>YAML layout (one entry per normalized name; {@code bound-uuid} is absent while unbound):
 * <pre>
 * ".steve":
 *   name: ".Steve"
 *   bound-uuid: "0f1e2d3c-..."
 *   registered-at: 2026-08-06T12:34:56Z
 * </pre>
 *
 * <p>If a {@code registered-at} value is absent (hand-edited or legacy file), the entry is
 * assigned {@link java.time.Instant#EPOCH} so that repeated loads of the same file always produce
 * identical state. The next flush writes the sentinel back, making the file self-healing.
 */
public final class BedrockAuthorizationData
        extends DataFile<Map<String, PendingBedrockAuthorization>> {

    public BedrockAuthorizationData(Path dataFolder) {
        super(dataFolder, "data/bedrock-authorizations.yml");
    }

    @Override
    protected Map<String, PendingBedrockAuthorization> load(ConfigurationNode node)
            throws SerializationException {
        Map<String, PendingBedrockAuthorization> map = new HashMap<>();
        for (var entry : node.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            ConfigurationNode child = entry.getValue();
            String name = child.node("name").getString(key);
            String boundStr = child.node("bound-uuid").getString("");
            UUID boundUuid = boundStr.isEmpty() ? null : UUID.fromString(boundStr);
            String registeredStr = child.node("registered-at").getString("");
            Instant registeredAt = registeredStr.isEmpty() ? Instant.EPOCH : Instant.parse(registeredStr);
            map.put(key, new PendingBedrockAuthorization(key, name, boundUuid, registeredAt));
        }
        return map;
    }

    @Override
    protected void save(Map<String, PendingBedrockAuthorization> state, ConfigurationNode node)
            throws SerializationException {
        node.set(null);
        for (var entry : state.entrySet()) {
            PendingBedrockAuthorization auth = entry.getValue();
            ConfigurationNode child = node.node(entry.getKey());
            child.node("name").set(auth.name());
            if (auth.boundUuid() != null) {
                child.node("bound-uuid").set(auth.boundUuid().toString());
            }
            child.node("registered-at").set(auth.registeredAt().toString());
        }
    }

    @Override
    protected Map<String, PendingBedrockAuthorization> empty() {
        return new HashMap<>();
    }
}
