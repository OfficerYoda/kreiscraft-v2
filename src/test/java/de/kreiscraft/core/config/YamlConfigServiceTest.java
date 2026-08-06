package de.kreiscraft.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;

class YamlConfigServiceTest {

    @Test
    void firstRun_copiesDefaultsFromClasspath(@TempDir Path tmp) throws IOException {
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());
        assertThat(Files.exists(tmp.resolve("config.yml"))).isTrue();
        assertThat(cfg.root().node("core", "language").getString()).isEqualTo("de");
    }

    @Test
    void reload_picksUpChangesOnDisk(@TempDir Path tmp) throws IOException {
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());
        Files.writeString(tmp.resolve("config.yml"),
                "core:\n  language: en\nmodules: {}\n");
        cfg.reload();
        assertThat(cfg.root().node("core", "language").getString()).isEqualTo("en");
    }

    @Test
    void module_returnsSubtree(@TempDir Path tmp) throws IOException {
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());
        Files.writeString(tmp.resolve("config.yml"),
                "modules:\n  combat:\n    enabled: true\n    tag-duration: 10s\n");
        cfg.reload();
        assertThat(cfg.module("combat").node("enabled").getBoolean()).isTrue();
        assertThat(cfg.module("combat").node("tag-duration").getString()).isEqualTo("10s");
    }

    @Test
    void everyModuleSection_hasEnabledKey(@TempDir Path tmp) throws IOException {
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());
        ConfigurationNode modules = cfg.root().node("modules");
        List<String> ids = List.of(
                "admin", "anti-cheat", "bedrock", "bot", "chat-format", "clickthrough",
                "combat", "effects", "firework", "glow", "invsee", "jail", "plugin-hider",
                "positions", "reboot", "settings-gui", "sign-color", "spawn",
                "start-sequence", "status", "tablist", "update-mode", "wither", "world-mgmt");
        for (String id : ids) {
            assertThat(modules.node(id).isNull())
                    .as("modules.%s should be present in config.yml", id)
                    .isFalse();
            assertThat(modules.node(id, "enabled").getBoolean())
                    .as("modules.%s.enabled should default to false", id)
                    .isFalse();
        }
    }
}
