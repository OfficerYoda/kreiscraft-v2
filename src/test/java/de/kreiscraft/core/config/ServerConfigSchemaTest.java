package de.kreiscraft.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

class ServerConfigSchemaTest {

    private static ConfigurationNode modulesRoot(String... moduleIds) throws SerializationException {
        ConfigurationNode root = CommentedConfigurationNode.root();
        for (String id : moduleIds) {
            root.node("modules", id, "enabled").set(true);
        }
        return root;
    }

    @Test
    void fromRoot_declaresEveryModuleEnableFlagAsRestartOnly() throws SerializationException {
        ServerConfigSchema schema = ServerConfigSchema.fromRoot(modulesRoot("combat", "sit"));

        List<ConfigField> moduleFlags =
                schema.fields().stream()
                        .filter(field -> field.path().size() == 3)
                        .filter(field -> "enabled".equals(field.path().get(2)))
                        .toList();
        assertThat(moduleFlags).hasSize(2);
        for (ConfigField field : moduleFlags) {
            assertThat(field.path().get(0)).isEqualTo("modules");
            assertThat(field.type()).isEqualTo(ConfigFieldType.BOOLEAN);
            assertThat(field.guiExposed()).isTrue();
            assertThat(field.lifecycle()).isEqualTo(ConfigLifecycle.RESTART_REQUIRED);
            assertThat(field.restartRequired()).isTrue();
            assertThat(field.hotReloadable()).isFalse();
        }
    }

    @Test
    void fromRoot_includesEveryEditableField() throws SerializationException {
        ServerConfigSchema schema = ServerConfigSchema.fromRoot(modulesRoot("combat", "effects"));

        List<List<Object>> exposedPaths =
                schema.guiExposed().stream().map(ConfigField::path).toList();
        for (ConfigField editable : ServerConfigSchema.editableFields()) {
            assertThat(exposedPaths)
                    .as("editable field %s is GUI-exposed", editable.path())
                    .contains(editable.path());
        }
    }

    @Test
    void editableFields_coverEverySupportedValueType() {
        ServerConfigSchema schema = new ServerConfigSchema(ServerConfigSchema.editableFields());

        assertThat(schema.guiExposed())
                .extracting(ConfigField::type)
                .contains(
                        ConfigFieldType.BOOLEAN,
                        ConfigFieldType.INTEGER,
                        ConfigFieldType.DOUBLE,
                        ConfigFieldType.DURATION,
                        ConfigFieldType.ENUM,
                        ConfigFieldType.LOCATION,
                        ConfigFieldType.STRING,
                        ConfigFieldType.STRING_LIST);
        for (ConfigField field : ServerConfigSchema.editableFields()) {
            assertThat(field.guiExposed()).as("%s is GUI-exposed", field.path()).isTrue();
        }
    }

    @Test
    void editableFields_exposeEveryApprovedSafeField() {
        ServerConfigSchema schema = new ServerConfigSchema(ServerConfigSchema.editableFields());
        List<List<Object>> exposed = schema.guiExposed().stream().map(ConfigField::path).toList();

        assertThat(exposed)
                .contains(
                        List.of("core", "language"),
                        List.of("modules", "admin", "links", "website"),
                        List.of("modules", "anti-cheat", "kick-on-fabric-forge"),
                        List.of("modules", "anti-cheat", "handshake-grace-duration"),
                        List.of("modules", "bedrock", "prefix"),
                        List.of("modules", "chat-format", "format"),
                        List.of("modules", "combat", "tag-duration"),
                        List.of("modules", "effects", "default-particle"),
                        List.of("modules", "effects", "schedule"),
                        List.of("modules", "effects", "offset-y"),
                        List.of("modules", "effects", "force"),
                        List.of("modules", "firework", "custom-model-data"),
                        List.of("modules", "jail", "location"),
                        List.of("modules", "jail", "blocked-commands"),
                        List.of("modules", "positions", "gui-rows"),
                        List.of("modules", "reboot", "default-countdown"),
                        List.of("modules", "spawn", "spawn-radius"),
                        List.of("modules", "spawn", "glide", "enabled"),
                        List.of("modules", "spawn", "glide-boost", "boost-strength"),
                        List.of("modules", "start-sequence", "border-duration"),
                        List.of("modules", "tablist", "update-interval"),
                        List.of("modules", "update-mode", "bypass-names"),
                        List.of("modules", "wither", "spawn-explosion-power"),
                        List.of("modules", "wither", "half-health-explosion-block-damage"),
                        List.of("modules", "wither", "death-boss-bar-radius"));
    }

    @Test
    void editableFields_hideCredentialsNetworkingAndOperatorOwnedState() {
        ServerConfigSchema schema = new ServerConfigSchema(ServerConfigSchema.editableFields());
        List<List<Object>> exposed = schema.guiExposed().stream().map(ConfigField::path).toList();

        assertThat(exposed)
                .as("risky, networking and operator-owned values stay YAML-only")
                .noneMatch(
                        path -> path.contains("admin-api-key")
                                || path.contains("host")
                                || path.contains("port")
                                || path.contains("cors-origins")
                                || path.contains("session-cookie")
                                || path.contains("channel")
                                || path.contains("header")
                                || path.contains("footer")
                                || path.contains("timezone")
                                || path.contains("debug")
                                || path.contains("auto-save-interval")
                                || path.contains("active")
                                || path.contains("has-started"));
    }

    @Test
    void editableFields_classifyLifecycleAccurately() {
        ServerConfigSchema schema = new ServerConfigSchema(ServerConfigSchema.editableFields());

        assertThat(lifecycle(schema, "core", "language"))
                .isEqualTo(ConfigLifecycle.RESTART_REQUIRED);
        assertThat(lifecycle(schema, "modules", "tablist", "update-interval"))
                .isEqualTo(ConfigLifecycle.RESTART_REQUIRED);
        assertThat(lifecycle(schema, "modules", "firework", "custom-model-data"))
                .isEqualTo(ConfigLifecycle.RESTART_REQUIRED);
        assertThat(lifecycle(schema, "modules", "combat", "tag-duration"))
                .isEqualTo(ConfigLifecycle.HOT_RELOAD);
        assertThat(lifecycle(schema, "modules", "spawn", "spawn-radius"))
                .isEqualTo(ConfigLifecycle.HOT_RELOAD);
        assertThat(lifecycle(schema, "modules", "wither", "death-explosion-power"))
                .isEqualTo(ConfigLifecycle.HOT_RELOAD);
    }

    @Test
    void performanceBar_exposesAllThresholdsAndFillEndpointsAsHotReloadableNumbers() {
        var fields = ServerConfigSchema.editableFields().stream()
                .filter(field -> field.path().size() == 4 && field.path().get(1).equals("admin")
                        && field.path().get(2).equals("tps-bar"))
                .toList();

        assertThat(fields).extracting(field -> field.path().get(3)).containsExactly(
                "mspt-green-max", "mspt-yellow-max", "tps-green-min", "tps-yellow-min",
                "ping-green-max", "ping-yellow-max", "mspt-empty", "mspt-full");
        for (ConfigField field : fields) {
            assertThat(field.guiExposed()).isTrue();
            assertThat(field.hotReloadable()).isTrue();
            assertThat(field.type()).isEqualTo(field.path().get(3).toString().startsWith("ping-")
                    ? ConfigFieldType.INTEGER
                    : ConfigFieldType.DOUBLE);
        }
    }

    @Test
    void discreteFields_declareCyclingOptions() {
        for (ConfigField field : ServerConfigSchema.editableFields()) {
            if (field.type() == ConfigFieldType.ENUM
                    || field.type() == ConfigFieldType.DURATION
                    || field.type() == ConfigFieldType.MATERIAL) {
                assertThat(field.options())
                        .as("%s declares options for its control", field.path())
                        .isNotEmpty();
            }
        }
    }

    private static ConfigLifecycle lifecycle(ServerConfigSchema schema, Object... path) {
        return schema.find(List.of(path)).orElseThrow().lifecycle();
    }

    @Test
    void find_returnsDeclaredFieldByPath() throws SerializationException {
        ServerConfigSchema schema = ServerConfigSchema.fromRoot(modulesRoot("combat"));

        assertThat(schema.find(List.of("modules", "combat", "enabled"))).isPresent();
        assertThat(schema.find(List.of("modules", "sit", "enabled"))).isEmpty();
    }

    @Test
    void guiExposed_returnsOnlyExposedFields() {
        ConfigField hidden =
                new ConfigField(
                        List.of("core", "debug"),
                        ConfigFieldType.BOOLEAN,
                        false,
                        ConfigLifecycle.HOT_RELOAD);
        ServerConfigSchema schema =
                new ServerConfigSchema(
                        List.of(hidden, ServerConfigSchema.moduleEnabled("combat")));

        assertThat(schema.guiExposed()).containsExactly(ServerConfigSchema.moduleEnabled("combat"));
    }

    @Test
    void moduleEnabled_validatorRejectsNonBoolean() throws SerializationException {
        ConfigField field = ServerConfigSchema.moduleEnabled("combat");
        ConfigurationNode value = CommentedConfigurationNode.root();
        value.set("yes");

        assertThat(field.validator().validate(value)).isPresent();
    }

    @Test
    void operatorStateFields_declareExpectedPathsAndLifecycle() {
        ConfigField location = ServerConfigSchema.spawnLocation();
        assertThat(location.path()).containsExactly("modules", "spawn", "location");
        assertThat(location.type()).isEqualTo(ConfigFieldType.LOCATION);
        assertThat(location.guiExposed()).isTrue();
        assertThat(location.lifecycle()).isEqualTo(ConfigLifecycle.HOT_RELOAD);

        ConfigField jail = ServerConfigSchema.jailLocation();
        assertThat(jail.path()).containsExactly("modules", "jail", "location");
        assertThat(jail.type()).isEqualTo(ConfigFieldType.LOCATION);
        assertThat(jail.guiExposed()).isTrue();
        assertThat(jail.lifecycle()).isEqualTo(ConfigLifecycle.HOT_RELOAD);

        ConfigField active = ServerConfigSchema.updateModeActive();
        assertThat(active.path()).containsExactly("modules", "update-mode", "active");
        assertThat(active.type()).isEqualTo(ConfigFieldType.BOOLEAN);

        ConfigField started = ServerConfigSchema.startSequenceStarted();
        assertThat(started.path()).containsExactly("modules", "start-sequence", "has-started");
        assertThat(started.type()).isEqualTo(ConfigFieldType.BOOLEAN);

        for (ConfigField field : List.of(location, jail, active, started)) {
            assertThat(field.hotReloadable()).isTrue();
            assertThat(field.restartRequired()).isFalse();
        }
    }

    @Test
    void operatorStateBooleanFields_rejectNonBooleanCandidates() throws SerializationException {
        ConfigurationNode notBoolean = CommentedConfigurationNode.root();
        notBoolean.set("yes");

        assertThat(ServerConfigSchema.updateModeActive().validator().validate(notBoolean))
                .isPresent();
        assertThat(ServerConfigSchema.startSequenceStarted().validator().validate(notBoolean))
                .isPresent();
    }

    @Test
    void lifecycle_describesHotReloadAndRestart() {
        assertThat(ConfigLifecycle.HOT_RELOAD.hotReloadable()).isTrue();
        assertThat(ConfigLifecycle.HOT_RELOAD.restartRequired()).isFalse();
        assertThat(ConfigLifecycle.RESTART_REQUIRED.hotReloadable()).isFalse();
        assertThat(ConfigLifecycle.RESTART_REQUIRED.restartRequired()).isTrue();
        assertThat(ConfigLifecycle.HOT_RELOAD.messageKey())
                .isEqualTo("server-config.lifecycle.hot-reload");
        assertThat(ConfigLifecycle.RESTART_REQUIRED.messageKey())
                .isEqualTo("server-config.lifecycle.restart-required");
    }
}
