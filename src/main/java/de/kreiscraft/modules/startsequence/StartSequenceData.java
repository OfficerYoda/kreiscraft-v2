package de.kreiscraft.modules.startsequence;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class StartSequenceData extends DataFile<boolean[]> {

    public StartSequenceData(Path dataFolder) {
        super(dataFolder, "data/start-sequence.yml");
    }

    public boolean hasStarted() {
        boolean[] arr = get();
        return arr != null && arr[0];
    }

    public void setStarted(boolean started) {
        update(arr -> arr[0] = started);
    }

    @Override
    protected boolean[] load(ConfigurationNode node) throws SerializationException {
        return new boolean[] {node.node("has-started").getBoolean(false)};
    }

    @Override
    protected void save(boolean[] state, ConfigurationNode node) throws SerializationException {
        node.node("has-started").set(state[0]);
    }

    @Override
    protected boolean[] empty() {
        return new boolean[] {false};
    }
}
