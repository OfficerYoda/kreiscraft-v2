package de.kreiscraft.core.config;

import java.io.IOException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public interface ConfigService {

    ConfigurationNode root();

    default ConfigurationNode module(String id) {
        return root().node("modules", id);
    }

    void reload() throws IOException;

    void register(DataFile<?> dataFile); // implemented once DataFile lands in Task 11

    /** Unmodifiable live view of every DataFile registered so far. */
    List<DataFile<?>> registeredDataFiles();
}
