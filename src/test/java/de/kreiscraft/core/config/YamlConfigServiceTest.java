package de.kreiscraft.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
