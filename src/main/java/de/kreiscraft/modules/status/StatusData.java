package de.kreiscraft.modules.status;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class StatusData extends DataFile<Map<UUID, Status>> {

    public StatusData(Path dataFolder) {
        super(dataFolder, "data/statuses.yml");
    }

    @Override
    protected Map<UUID, Status> load(ConfigurationNode node) throws SerializationException {
        Map<UUID, Status> map = new HashMap<>();
        for (var entry : node.childrenMap().entrySet()) {
            UUID id = UUID.fromString(String.valueOf(entry.getKey()));
            ConfigurationNode n = entry.getValue();
            String prefix = n.node("prefix").getString(Status.defaults().prefix());
            String suffix = n.node("suffix").getString("");
            int color = n.node("color").getInt(0xAAAAAA);
            map.put(id, new Status(prefix, suffix, color));
        }
        return map;
    }

    @Override
    protected void save(Map<UUID, Status> state, ConfigurationNode node) throws SerializationException {
        node.set(null);
        for (var entry : state.entrySet()) {
            ConfigurationNode n = node.node(entry.getKey().toString());
            Status s = entry.getValue();
            n.node("prefix").set(s.prefix());
            n.node("suffix").set(s.suffix());
            n.node("color").set(s.color());
        }
    }

    @Override
    protected Map<UUID, Status> empty() {
        return new HashMap<>();
    }
}
