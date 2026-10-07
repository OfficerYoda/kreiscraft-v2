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
     * root, followed by the representative safe fields the Server Configuration GUI exposes.
     */
    public static ServerConfigSchema fromRoot(ConfigurationNode root) {
        Objects.requireNonNull(root, "root");
        List<ConfigField> fields = new ArrayList<>();
        for (Object moduleId : root.node("modules").childrenMap().keySet()) {
            fields.add(moduleEnabled(String.valueOf(moduleId)));
        }
        fields.addAll(representativeFields());
        return new ServerConfigSchema(fields);
    }

    /**
     * The initial representative set of safe, GUI-editable module fields.
     *
     * <p>One entry per value shape the Server Configuration GUI supports: boolean, whole number,
     * decimal number, duration, enum, location, text, and text list. Every one of these is
     * hot-reloadable — the owning module reads the new value the next time {@code /kreiscraft
     * reload} runs. Module enable flags are declared separately by {@link #moduleEnabled(String)}
     * and are always restart-only.
     *
     * <p>Fields that are low-level, sensitive, networking, or the tab-list header/footer are
     * deliberately absent: they stay YAML-only.
     */
    public static List<ConfigField> representativeFields() {
        List<ConfigField> fields = new ArrayList<>();
        fields.add(new ConfigField(
                List.of("modules", "combat", "disable-elytra"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                true,
                ConfigLifecycle.HOT_RELOAD));
        fields.add(new ConfigField(
                List.of("modules", "effects", "count"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(1, 1_000),
                true,
                ConfigLifecycle.HOT_RELOAD));
        fields.add(new ConfigField(
                List.of("modules", "effects", "offset-x"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(0.0, 10.0),
                true,
                ConfigLifecycle.HOT_RELOAD));
        fields.add(new ConfigField(
                List.of("modules", "combat", "tag-duration"),
                ConfigFieldType.DURATION,
                ConfigValidators.duration(),
                true,
                ConfigLifecycle.HOT_RELOAD));
        fields.add(new ConfigField(
                List.of("modules", "combat", "default-display"),
                ConfigFieldType.ENUM,
                ConfigValidators.oneOf("CHAT", "ACTIONBAR", "TITLE"),
                true,
                ConfigLifecycle.HOT_RELOAD));
        fields.add(spawnLocation());
        fields.add(new ConfigField(
                List.of("modules", "chat-format", "format"),
                ConfigFieldType.STRING,
                ConfigValidators.nonEmptyString(),
                true,
                ConfigLifecycle.HOT_RELOAD));
        fields.add(new ConfigField(
                List.of("modules", "combat", "excluded-worlds"),
                ConfigFieldType.STRING_LIST,
                ConfigValidators.stringList(),
                true,
                ConfigLifecycle.HOT_RELOAD));
        return fields;
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
    // failure cannot leave the running server and config.yml disagreeing. spawn.location is also
    // exposed in the Server Configuration GUI as its representative location control; the remaining
    // operator-owned values stay YAML-only because editing them in-game would bypass the command
    // side effects (for example disconnecting non-bypassed players when maintenance mode starts).

    /**
     * Operator-owned spawn {@link org.bukkit.Location}; absent until {@code /setspawn} is used. Also
     * the Server Configuration GUI's representative location control.
     */
    public static ConfigField spawnLocation() {
        return new ConfigField(
                List.of("modules", "spawn", "location"),
                ConfigFieldType.LOCATION,
                ConfigValidators.alwaysValid(),
                true,
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
