package de.kreiscraft.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The declared set of editable Server Configuration fields.
 *
 * <p>The schema is derived from the configuration tree so that the module enable flags present in
 * the bundled {@code config.yml} are always classified. Every module enable flag is registered as
 * {@link ConfigLifecycle#RESTART_REQUIRED}: changing it only takes effect on the next server start.
 *
 * <p>Later stages of the remediation extend this schema with the remaining safe fields. The
 * editing boundary itself ({@link ConfigService#edit(ConfigEdit)}) does not require a field to be
 * present here; the schema is what the Server Configuration GUI enumerates.
 */
public final class ServerConfigSchema {

    private final List<ConfigField> fields;

    public ServerConfigSchema(List<ConfigField> fields) {
        this.fields = List.copyOf(fields);
    }

    /**
     * Builds a schema declaring the module enable flags found under {@code modules} in the given
     * root.
     */
    public static ServerConfigSchema fromRoot(ConfigurationNode root) {
        Objects.requireNonNull(root, "root");
        List<ConfigField> fields = new ArrayList<>();
        for (Object moduleId : root.node("modules").childrenMap().keySet()) {
            fields.add(moduleEnabled(String.valueOf(moduleId)));
        }
        return new ServerConfigSchema(fields);
    }

    /**
     * Declares the {@code enabled} flag of one module. Module enablement is always restart-only:
     * {@code /kreiscraft reload} does not start or stop modules.
     */
    public static ConfigField moduleEnabled(String moduleId) {
        Objects.requireNonNull(moduleId, "moduleId");
        return new ConfigField(
                List.of("modules", moduleId, "enabled"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                true,
                ConfigLifecycle.RESTART_REQUIRED);
    }

    // ── operator-owned state ────────────────────────────────────────────────
    //
    // ADR-10 puts spawn location, update-mode state and start-sequence completion in Server
    // Configuration. The fields below are the write metadata for those values; commands such as
    // /setspawn and /updatemode go through ConfigService.applyEdit so an invalid value or an I/O
    // failure cannot leave the running server and config.yml disagreeing. They become visible in
    // the Server Configuration GUI when its own stage exposes them, so they are declared
    // not-yet-GUI-exposed here.

    /** Operator-owned spawn {@link org.bukkit.Location}; absent until {@code /setspawn} is used. */
    public static ConfigField spawnLocation() {
        return new ConfigField(
                List.of("modules", "spawn", "location"),
                ConfigFieldType.LOCATION,
                ConfigValidators.alwaysValid(),
                false,
                ConfigLifecycle.HOT_RELOAD);
    }

    /** Operator-owned jail {@link org.bukkit.Location}; written by {@code /setjail}. */
    public static ConfigField jailLocation() {
        return new ConfigField(
                List.of("modules", "jail", "location"),
                ConfigFieldType.LOCATION,
                ConfigValidators.alwaysValid(),
                false,
                ConfigLifecycle.HOT_RELOAD);
    }

    /** Operator-owned {@code /spawn} teleport range, in blocks. */
    public static ConfigField spawnTeleportRange() {
        return new ConfigField(
                List.of("modules", "spawn", "teleport-range"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(1, Integer.MAX_VALUE),
                false,
                ConfigLifecycle.HOT_RELOAD);
    }

    /** Operator-owned maintenance-mode state. */
    public static ConfigField updateModeActive() {
        return new ConfigField(
                List.of("modules", "update-mode", "active"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                false,
                ConfigLifecycle.HOT_RELOAD);
    }

    /** Operator-owned season-start completion flag. */
    public static ConfigField startSequenceStarted() {
        return new ConfigField(
                List.of("modules", "start-sequence", "has-started"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                false,
                ConfigLifecycle.HOT_RELOAD);
    }

    /** Every declared field. */
    public List<ConfigField> fields() {
        return fields;
    }

    /** The declared fields that may be shown in the Server Configuration GUI. */
    public List<ConfigField> guiExposed() {
        return fields.stream().filter(ConfigField::guiExposed).toList();
    }

    /** Finds the field declared at an exact path, if any. */
    public Optional<ConfigField> find(List<?> path) {
        return fields.stream().filter(field -> field.path().equals(path)).findFirst();
    }
}
