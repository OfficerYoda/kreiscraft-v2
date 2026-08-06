package de.kreiscraft.modules.bot;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class BotData extends DataFile<Map<String, BotRecord>> {

    public BotData(Path dataFolder) {
        super(dataFolder, "data/bot.yml");
    }

    @Override
    protected Map<String, BotRecord> load(ConfigurationNode node) throws SerializationException {
        Map<String, BotRecord> map = new LinkedHashMap<>();
        for (var entry : node.node("bots").childrenMap().entrySet()) {
            String name = String.valueOf(entry.getKey());
            map.put(name, entry.getValue().get(BotRecord.class));
        }
        return map;
    }

    @Override
    protected void save(Map<String, BotRecord> state, ConfigurationNode node) throws SerializationException {
        node.node("bots").set(null);
        for (var entry : state.entrySet()) {
            node.node("bots", entry.getKey()).set(BotRecord.class, entry.getValue());
        }
    }

    @Override
    protected Map<String, BotRecord> empty() {
        return new LinkedHashMap<>();
    }
}
