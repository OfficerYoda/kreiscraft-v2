package de.kreiscraft.modules.updatemode;

import java.nio.file.Path;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class UpdateModeData extends de.kreiscraft.core.config.DataFile<boolean[]> {

    public UpdateModeData(Path dataFolder) {
        super(dataFolder, "data/update-mode.yml");
    }

    @Override
    protected boolean[] load(ConfigurationNode node) throws SerializationException {
        return new boolean[] {node.node("active").getBoolean(false)};
    }

    @Override
    protected void save(boolean[] state, ConfigurationNode node) throws SerializationException {
        node.node("active").set(state[0]);
    }

    @Override
    protected boolean[] empty() {
        return new boolean[] {false};
    }
}
