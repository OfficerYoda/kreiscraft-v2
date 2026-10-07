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

    /**
     * The declared set of editable Server Configuration fields.
     *
     * <p>Every declared field reports its value type, validation, GUI exposure and reload/restart
     * lifecycle. Module enable flags are always restart-only.
     */
    ServerConfigSchema configSchema();

    /**
     * Validates and atomically persists an edit to one declared field.
     *
     * <p>The value is applied to a detached copy of the live configuration and validated there.
     * Nothing is changed unless the complete candidate is valid and has been durably written; see
     * {@link ConfigEditResult} for the possible outcomes.
     */
    ConfigEditResult edit(ConfigEdit edit);
}
