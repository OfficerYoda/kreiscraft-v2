package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigEditResult;
import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ServerConfigSchema;

/**
 * Read/write view of Server Configuration used by the Server Configuration GUI.
 *
 * <p>The GUI owns no persistence: every read returns the current value from the live configuration
 * tree and every write goes through the validated, atomic {@code ConfigService} edit boundary. A
 * rejected or failed edit changes neither the running configuration nor {@code config.yml}.
 */
public interface ServerConfigService {

    /** The declared fields, including the safe set the GUI enumerates. */
    ServerConfigSchema schema();

    /**
     * The current value of a declared field, in the editing representation for its type, or {@code
     * null} when the field is absent from the live configuration.
     */
    Object current(ConfigField field);

    /** Validates and atomically persists an edit to one field. */
    ConfigEditResult edit(ConfigField field, Object value);
}
