package de.kreiscraft.modules.pluginhider;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class PluginHiderData extends DataFile<List<String>> {

    public PluginHiderData(Path dataFolder) {
        super(dataFolder, "plugin-hider.yml");
    }

    @Override
    protected List<String> load(ConfigurationNode node) throws SerializationException {
        List<String> raw = node.node("allowed").getList(String.class);
        return raw != null ? new ArrayList<>(raw) : new ArrayList<>();
    }

    @Override
    protected void save(List<String> state, ConfigurationNode node) throws SerializationException {
        node.node("allowed").setList(String.class, state);
    }

    @Override
    protected List<String> empty() {
        return new ArrayList<>();
    }
}
