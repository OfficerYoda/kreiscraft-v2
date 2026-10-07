package de.kreiscraft.core.config;

import java.util.List;
import java.util.Objects;

/**
 * Metadata for one editable Server Configuration field.
 *
 * <p>A field declares everything the editing boundary needs to know before it accepts a value:
 * the value {@link #type()}, the {@link #validator()}, whether the field is {@link #guiExposed()},
 * and its {@link #lifecycle()}. Module enable flags are always declared with {@link
 * ConfigLifecycle#RESTART_REQUIRED}; see {@link ServerConfigSchema#moduleEnabled(String)}.
 *
 * @param path absolute node path from the configuration root (for example {@code ["modules",
 *     "combat", "enabled"]})
 * @param type the value shape used to choose a GUI control
 * @param validator the validation applied to a detached candidate value
 * @param guiExposed whether the field may be shown in the Server Configuration GUI
 * @param lifecycle whether the value hot-reloads or requires a restart
 */
public record ConfigField(
        List<Object> path,
        ConfigFieldType type,
        ConfigValidator validator,
        boolean guiExposed,
        ConfigLifecycle lifecycle) {

    public ConfigField {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        path = List.copyOf(path);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(lifecycle, "lifecycle");
    }

    /** Creates a field that accepts any value. */
    public ConfigField(
            List<Object> path,
            ConfigFieldType type,
            boolean guiExposed,
            ConfigLifecycle lifecycle) {
        this(path, type, ConfigValidators.alwaysValid(), guiExposed, lifecycle);
    }

    /** The path as a varargs array, suitable for {@code ConfigurationNode.node(Object...)}. */
    public Object[] pathArray() {
        return path.toArray();
    }

    /** Whether {@code /kreiscraft reload} applies this field without a restart. */
    public boolean hotReloadable() {
        return lifecycle.hotReloadable();
    }

    /** Whether a full server restart is required before the value is applied. */
    public boolean restartRequired() {
        return lifecycle.restartRequired();
    }
}
