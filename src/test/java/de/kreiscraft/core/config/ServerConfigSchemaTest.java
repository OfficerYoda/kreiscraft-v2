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
        ServerConfigSchema schema = ServerConfigSchema.fromRoot(modulesRoot("combat", "glow"));

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
    void fromRoot_includesRepresentativeSafeFields() throws SerializationException {
        ServerConfigSchema schema = ServerConfigSchema.fromRoot(modulesRoot("combat", "effects"));

        List<List<Object>> exposedPaths =
                schema.guiExposed().stream().map(ConfigField::path).toList();
        for (ConfigField representative : ServerConfigSchema.representativeFields()) {
            assertThat(exposedPaths)
                    .as("representative field %s is GUI-exposed", representative.path())
                    .contains(representative.path());
        }
    }

    @Test
    void representativeFields_coverEverySupportedValueType() {
        ServerConfigSchema schema =
                new ServerConfigSchema(ServerConfigSchema.representativeFields());

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
        for (ConfigField field : ServerConfigSchema.representativeFields()) {
            assertThat(field.guiExposed()).as("%s is GUI-exposed", field.path()).isTrue();
            assertThat(field.hotReloadable())
                    .as("%s is hot-reloadable", field.path())
                    .isTrue();
        }
    }

    @Test
    void find_returnsDeclaredFieldByPath() throws SerializationException {
        ServerConfigSchema schema = ServerConfigSchema.fromRoot(modulesRoot("combat"));

        assertThat(schema.find(List.of("modules", "combat", "enabled"))).isPresent();
        assertThat(schema.find(List.of("modules", "glow", "enabled"))).isEmpty();
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

        ConfigField active = ServerConfigSchema.updateModeActive();
        assertThat(active.path()).containsExactly("modules", "update-mode", "active");
        assertThat(active.type()).isEqualTo(ConfigFieldType.BOOLEAN);

        ConfigField started = ServerConfigSchema.startSequenceStarted();
        assertThat(started.path()).containsExactly("modules", "start-sequence", "has-started");
        assertThat(started.type()).isEqualTo(ConfigFieldType.BOOLEAN);

        for (ConfigField field : List.of(location, active, started)) {
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
