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

        assertThat(schema.fields()).hasSize(2);
        for (ConfigField field : schema.fields()) {
            assertThat(field.path()).hasSize(3);
            assertThat(field.path().get(0)).isEqualTo("modules");
            assertThat(field.path().get(2)).isEqualTo("enabled");
            assertThat(field.type()).isEqualTo(ConfigFieldType.BOOLEAN);
            assertThat(field.guiExposed()).isTrue();
            assertThat(field.lifecycle()).isEqualTo(ConfigLifecycle.RESTART_REQUIRED);
            assertThat(field.restartRequired()).isTrue();
            assertThat(field.hotReloadable()).isFalse();
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
