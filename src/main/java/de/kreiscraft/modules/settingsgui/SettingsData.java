package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class SettingsData extends DataFile<Map<UUID, Settings>> {

    public SettingsData(Path dataFolder) {
        super(dataFolder, "settings.yml");
    }

    @Override
    protected Map<UUID, Settings> load(ConfigurationNode node) throws SerializationException {
        Map<UUID, Settings> map = new HashMap<>();
        for (var entry : node.childrenMap().entrySet()) {
            UUID id = UUID.fromString(String.valueOf(entry.getKey()));
            Settings s = entry.getValue().get(Settings.class);
            if (s != null) {
                map.put(id, s);
            }
        }
        return map;
    }

    @Override
    protected void save(Map<UUID, Settings> state, ConfigurationNode node) throws SerializationException {
        node.set(null);
        for (var entry : state.entrySet()) {
            node.node(entry.getKey().toString()).set(Settings.class, entry.getValue());
        }
    }

    @Override
    protected Map<UUID, Settings> empty() {
        return new HashMap<>();
    }
}
