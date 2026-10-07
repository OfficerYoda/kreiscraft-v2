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

    /**
     * Applies one edit and requires it to be durably written.
     *
     * <p>Commands that change operator-owned state have no useful recovery from a rejected or
     * failed write, so this convenience method turns the {@link ConfigEditResult} into an
     * exception. A rejection is a programming error in the caller (the value should have been
     * valid) and an I/O failure is an operational problem; both leave the live configuration and
     * the file untouched, exactly as {@link #edit(ConfigEdit)} does.
     *
     * @throws IllegalArgumentException when the value is rejected by the field validator
     * @throws IllegalStateException when the candidate could not be persisted
     */
    default void applyEdit(ConfigField field, Object value) {
        ConfigEditResult result = edit(new ConfigEdit(field, value));
        if (result instanceof ConfigEditResult.Rejected rejected) {
            throw new IllegalArgumentException(
                    "rejected Server Configuration edit for " + field.path() + ": " + rejected.reason());
        }
        if (result instanceof ConfigEditResult.Failed failed) {
            throw new IllegalStateException(
                    "failed Server Configuration edit for " + field.path(), failed.cause());
        }
    }
}
