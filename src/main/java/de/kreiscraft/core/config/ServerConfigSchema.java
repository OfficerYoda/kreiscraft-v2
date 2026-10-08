package de.kreiscraft.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Particle;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The declared set of editable Server Configuration fields.
 *
 * <p>The schema is derived from the configuration tree so that the module enable flags present in
 * the bundled {@code config.yml} are always classified. Every module enable flag is registered as
 * {@link ConfigLifecycle#RESTART_REQUIRED}: changing it only takes effect on the next server start.
 *
 * <p>{@link #editableFields()} declares every safe field the Server Configuration GUI enumerates.
 * The editing boundary itself ({@link ConfigService#edit(ConfigEdit)}) does not require a field to
 * be present here; the schema is what the GUI enumerates.
 */
public final class ServerConfigSchema {

    private final List<ConfigField> fields;

    public ServerConfigSchema(List<ConfigField> fields) {
        this.fields = List.copyOf(fields);
    }

    /**
     * Builds a schema declaring the module enable flags found under {@code modules} in the given
     * root, followed by every safe field the Server Configuration GUI exposes.
     */
    public static ServerConfigSchema fromRoot(ConfigurationNode root) {
        Objects.requireNonNull(root, "root");
        List<ConfigField> fields = new ArrayList<>();
        for (Object moduleId : root.node("modules").childrenMap().keySet()) {
            fields.add(moduleEnabled(String.valueOf(moduleId)));
        }
        fields.addAll(editableFields());
        return new ServerConfigSchema(fields);
    }

    /** Curated particle names cycled by the effects particle control. */
    private static final List<String> PARTICLE_OPTIONS = List.of(
            "HAPPY_VILLAGER",
            "FLAME",
            "SOUL_FIRE_FLAME",
            "END_ROD",
            "HEART",
            "CRIT",
            "ENCHANT",
            "CLOUD",
            "NOTE",
            "LAVA",
            "ELECTRIC_SPARK",
            "TOTEM_OF_UNDYING");

    /**
     * The complete safe, GUI-editable field set.
     *
     * <p>Every non-secret, non-infrastructure module tunable is declared here with the value shape,
     * validation bounds, GUI exposure and lifecycle that match how the owning module actually reads
     * it. A field is {@link ConfigLifecycle#HOT_RELOAD} only when the owning module re-reads the
     * value on {@code /kreiscraft reload}; anything read once at enable time is {@link
     * ConfigLifecycle#RESTART_REQUIRED}.
     *
     * <p>Deliberately absent so they stay YAML-only: the HTTP API's bind address, port, CORS
     * origins, session cookie, timeouts and {@code admin-api-key}; the anti-cheat plugin-messaging
     * channel; the tab-list header and footer; the {@code core} timezone and auto-save settings;
     * and the operator-owned runtime state written by commands ({@code update-mode.active} and
     * {@code start-sequence.has-started}).
     *
     * <p>The complete set is grouped by owning category in the GUI; see {@link
     * de.kreiscraft.modules.settingsgui.ServerConfigGui}.
     */
    public static List<ConfigField> editableFields() {
        List<ConfigField> fields = new ArrayList<>();

        // ── core ────────────────────────────────────────────────────────────
        // Server language is a restart-only enum: the message service captures the server language
        // when it is constructed and /kreiscraft reload only re-reads the language trees.
        fields.add(field(
                path("core", "language"),
                ConfigFieldType.ENUM,
                ConfigValidators.oneOf("de", "en"),
                ConfigLifecycle.RESTART_REQUIRED));

        // ── admin ───────────────────────────────────────────────────────────
        fields.add(field(
                path("modules", "admin", "links", "website"),
                ConfigFieldType.STRING,
                ConfigValidators.uri(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "admin", "links", "discord"),
                ConfigFieldType.STRING,
                ConfigValidators.uri(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "admin", "links", "youtube"),
                ConfigFieldType.STRING,
                ConfigValidators.uri(),
                ConfigLifecycle.HOT_RELOAD));

        // ── anti-cheat ──────────────────────────────────────────────────────
        // `channel` is a plugin-messaging channel (networking) and stays YAML-only.
        fields.add(field(
                path("modules", "anti-cheat", "kick-on-fabric-forge"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(duration(
                path("modules", "anti-cheat", "handshake-grace-duration"),
                List.of("1s", "3s", "5s", "10s", "30s")));

        // ── bedrock ─────────────────────────────────────────────────────────
        fields.add(field(
                path("modules", "bedrock", "prefix"),
                ConfigFieldType.STRING,
                ConfigValidators.nonEmptyString(),
                ConfigLifecycle.HOT_RELOAD));

        // ── chat-format ─────────────────────────────────────────────────────
        // `event-priority` is bound when the listener is registered and is not applied on reload, so
        // it stays YAML-only rather than claiming a live control it does not have.
        fields.add(field(
                path("modules", "chat-format", "allow-mini-message-in-body"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "chat-format", "format"),
                ConfigFieldType.STRING,
                ConfigValidators.miniMessage(),
                ConfigLifecycle.HOT_RELOAD));

        // ── combat ──────────────────────────────────────────────────────────
        fields.add(duration(
                path("modules", "combat", "tag-duration"),
                List.of("1s", "3s", "5s", "10s", "30s", "1m")));
        fields.add(field(
                path("modules", "combat", "disable-elytra"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "combat", "excluded-worlds"),
                ConfigFieldType.STRING_LIST,
                ConfigValidators.stringList(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "combat", "default-display"),
                ConfigFieldType.ENUM,
                ConfigValidators.oneOf("CHAT", "ACTIONBAR", "TITLE"),
                ConfigLifecycle.HOT_RELOAD));

        // ── effects ─────────────────────────────────────────────────────────
        fields.add(new ConfigField(
                path("modules", "effects", "default-particle"),
                ConfigFieldType.ENUM,
                ConfigValidators.enumValue(Particle.class),
                true,
                ConfigLifecycle.HOT_RELOAD,
                PARTICLE_OPTIONS));
        fields.add(duration(
                path("modules", "effects", "schedule"),
                List.of("1t", "5t", "10t", "20t", "1s")));
        fields.add(field(
                path("modules", "effects", "count"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(1, 10_000),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "effects", "offset-x"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(-10.0, 10.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "effects", "offset-y"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(-10.0, 10.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "effects", "offset-z"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(-10.0, 10.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "effects", "force"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                ConfigLifecycle.HOT_RELOAD));

        // ── firework ────────────────────────────────────────────────────────
        // The module reads these when the recipe is registered and has no reload hook.
        fields.add(field(
                path("modules", "firework", "custom-model-data"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(0, Integer.MAX_VALUE),
                ConfigLifecycle.RESTART_REQUIRED));
        fields.add(field(
                path("modules", "firework", "namespace-key"),
                ConfigFieldType.STRING,
                ConfigValidators.nonEmptyString(),
                ConfigLifecycle.RESTART_REQUIRED));

        // ── jail ────────────────────────────────────────────────────────────
        fields.add(jailLocation());
        fields.add(field(
                path("modules", "jail", "blocked-commands"),
                ConfigFieldType.STRING_LIST,
                ConfigValidators.stringList(),
                ConfigLifecycle.HOT_RELOAD));

        // ── positions ───────────────────────────────────────────────────────
        fields.add(field(
                path("modules", "positions", "gui-rows"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(1, 6),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "positions", "teleport-on-click"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                ConfigLifecycle.HOT_RELOAD));

        // ── reboot ──────────────────────────────────────────────────────────
        fields.add(duration(
                path("modules", "reboot", "default-countdown"),
                List.of("10s", "30s", "60s", "2m", "5m")));

        // ── spawn ───────────────────────────────────────────────────────────
        fields.add(spawnLocation());
        fields.add(field(
                path("modules", "spawn", "spawn-radius"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(1, 30_000_000),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "spawn", "glide", "enabled"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "spawn", "glide", "multiply-value"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(1, 100),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "spawn", "swap-hand-boost", "enabled"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "spawn", "swap-hand-boost", "vector-multiplier"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(0.1, 100.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "spawn", "glide-boost", "enabled"),
                ConfigFieldType.BOOLEAN,
                ConfigValidators.booleanValue(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "spawn", "glide-boost", "boost-strength"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(0.1, 100.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "spawn", "glide-boost", "cooldown-seconds"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(1, 3_600),
                ConfigLifecycle.HOT_RELOAD));

        // ── start-sequence ──────────────────────────────────────────────────
        fields.add(duration(
                path("modules", "start-sequence", "countdown"),
                List.of("5s", "10s", "15s", "30s")));
        fields.add(field(
                path("modules", "start-sequence", "initial-border-size"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(1.0, 30_000_000.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "start-sequence", "target-border-size"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(1.0, 30_000_000.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(duration(
                path("modules", "start-sequence", "border-duration"),
                List.of("600s", "3600s", "30000s")));

        // ── tablist ─────────────────────────────────────────────────────────
        // Header and footer templates stay YAML-only; the refresh interval is a restart-only
        // duration because the shared repeating task is not rescheduled on reload.
        fields.add(new ConfigField(
                path("modules", "tablist", "update-interval"),
                ConfigFieldType.DURATION,
                ConfigValidators.duration(),
                true,
                ConfigLifecycle.RESTART_REQUIRED,
                List.of("1s", "2s", "5s", "20t")));

        // ── update-mode ─────────────────────────────────────────────────────
        // `active` is written by /updatemode and stays YAML-only so the GUI cannot bypass the
        // command's disconnect side effects.
        fields.add(field(
                path("modules", "update-mode", "bypass-permission"),
                ConfigFieldType.STRING,
                ConfigValidators.nonEmptyString(),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "update-mode", "bypass-names"),
                ConfigFieldType.STRING_LIST,
                ConfigValidators.stringList(),
                ConfigLifecycle.HOT_RELOAD));

        // ── wither ──────────────────────────────────────────────────────────
        fields.add(field(
                path("modules", "wither", "max-health"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(1.0, 100_000.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "wither", "attack-damage"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(0.0, 100_000.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "wither", "armor"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(0.0, 1_000.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "wither", "half-health-threshold"),
                ConfigFieldType.DOUBLE,
                ConfigValidators.decimalRange(0.0, 100_000.0),
                ConfigLifecycle.HOT_RELOAD));
        fields.add(field(
                path("modules", "wither", "minion-count"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(0, 100),
                ConfigLifecycle.HOT_RELOAD));
        fields.addAll(witherPhase("spawn"));
        fields.addAll(witherPhase("half-health"));
        fields.addAll(witherPhase("death"));
        fields.add(field(
                path("modules", "wither", "death-boss-bar-radius"),
                ConfigFieldType.INTEGER,
                ConfigValidators.integerRange(1, 1_000),
                ConfigLifecycle.HOT_RELOAD));

        return fields;
    }

    /** The three independent explosion tunables for one Wither lifecycle phase. */
    private static List<ConfigField> witherPhase(String phase) {
        String prefix = phase + "-explosion";
        return List.of(
                field(
                        path("modules", "wither", prefix + "-power"),
                        ConfigFieldType.DOUBLE,
                        ConfigValidators.decimalRange(0.0, 1_000.0),
                        ConfigLifecycle.HOT_RELOAD),
                field(
                        path("modules", "wither", prefix + "-fire"),
                        ConfigFieldType.BOOLEAN,
                        ConfigValidators.booleanValue(),
                        ConfigLifecycle.HOT_RELOAD),
                field(
                        path("modules", "wither", prefix + "-block-damage"),
                        ConfigFieldType.BOOLEAN,
                        ConfigValidators.booleanValue(),
                        ConfigLifecycle.HOT_RELOAD));
    }

    private static ConfigField field(
            List<Object> path,
            ConfigFieldType type,
            ConfigValidator validator,
            ConfigLifecycle lifecycle) {
        return new ConfigField(path, type, validator, true, lifecycle);
    }

    private static ConfigField duration(List<Object> path, List<String> options) {
        return new ConfigField(
                path,
                ConfigFieldType.DURATION,
                ConfigValidators.duration(),
                true,
                ConfigLifecycle.HOT_RELOAD,
                options);
    }

    private static List<Object> path(Object... parts) {
        return List.of(parts);
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
    // Spawn location, jail location, update-mode state and start-sequence completion belong in
    // Server Configuration. The fields below are the write metadata for those values; commands such
    // as /setspawn, /setjail and /updatemode go through ConfigService.applyEdit so an invalid value
    // or an I/O failure cannot leave the running server and config.yml disagreeing. spawn.location
    // and jail.location are also exposed in the Server Configuration GUI as location controls; the
    // remaining operator-owned values stay YAML-only because editing them in-game would bypass the
    // command side effects (for example disconnecting non-bypassed players when maintenance mode
    // starts).

    /**
     * Operator-owned spawn {@link org.bukkit.Location}; absent until {@code /setspawn} is used. Also
     * the Server Configuration GUI's location control.
     */
    public static ConfigField spawnLocation() {
        return new ConfigField(
                List.of("modules", "spawn", "location"),
                ConfigFieldType.LOCATION,
                ConfigValidators.alwaysValid(),
                true,
                ConfigLifecycle.HOT_RELOAD);
    }

    /**
     * Operator-owned jail {@link org.bukkit.Location}; written by {@code /setjail}. Also the Server
     * Configuration GUI's jail location control.
     */
    public static ConfigField jailLocation() {
        return new ConfigField(
                List.of("modules", "jail", "location"),
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
