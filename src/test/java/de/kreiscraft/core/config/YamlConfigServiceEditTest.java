package de.kreiscraft.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class YamlConfigServiceEditTest {

    private static final String CUSTOM_CONFIG =
            "# Kreiscraft configuration.\n"
                    + "# Keep this header comment.\n"
                    + "core:\n"
                    + "  # language selection\n"
                    + "  language: de\n"
                    + "modules:\n"
                    + "  combat:\n"
                    + "    enabled: true   # combat toggle\n";

    private static ConfigField combatEnabled(YamlConfigService cfg) {
        return cfg.configSchema().find(List.of("modules", "combat", "enabled")).orElseThrow();
    }

    private static ConfigurationNode load(Path file) throws IOException {
        return YamlConfigurationLoader.builder().path(file).build().load();
    }

    @Test
    void appliedEdit_updatesLiveTreeAndDurableFile(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("config.yml"), CUSTOM_CONFIG);
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());

        ConfigEditResult result = cfg.edit(new ConfigEdit(combatEnabled(cfg), false));

        assertThat(result).isInstanceOf(ConfigEditResult.Applied.class);
        assertThat(cfg.root().node("modules", "combat", "enabled").getBoolean()).isFalse();
        assertThat(load(tmp.resolve("config.yml")).node("modules", "combat", "enabled").getBoolean())
                .isFalse();
        try (var files = Files.list(tmp)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactly("config.yml");
        }
    }

    @Test
    void appliedEdit_onBundledConfig_preservesUnrelatedValues(@TempDir Path tmp)
            throws IOException {
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());

        ConfigEditResult result = cfg.edit(new ConfigEdit(combatEnabled(cfg), false));

        assertThat(result).isInstanceOf(ConfigEditResult.Applied.class);
        String after = Files.readString(tmp.resolve("config.yml"));
        assertThat(after).doesNotContainPattern("(?m)(?:^|\\s)#");
        ConfigurationNode reloaded = load(tmp.resolve("config.yml"));
        assertThat(reloaded.node("modules", "combat", "enabled").getBoolean()).isFalse();
        assertThat(reloaded.node("core", "language").getString()).isEqualTo("de");
        assertThat(reloaded.node("modules", "spawn", "spawn-radius").getInt())
                .isEqualTo(50);
        assertThat(reloaded.node("modules", "jail", "location", "world").getString())
                .isEqualTo("world");
        assertThat(reloaded.node("modules", "reboot", "chat-seconds").childrenList()).hasSize(10);
    }

    @Test
    void rejectedEdit_changesNeitherLiveTreeNorFile(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("config.yml"), CUSTOM_CONFIG);
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());
        String before = Files.readString(tmp.resolve("config.yml"));

        ConfigEditResult result = cfg.edit(new ConfigEdit(combatEnabled(cfg), "not-a-boolean"));

        assertThat(result).isInstanceOf(ConfigEditResult.Rejected.class);
        assertThat(cfg.root().node("modules", "combat", "enabled").getBoolean()).isTrue();
        assertThat(Files.readString(tmp.resolve("config.yml"))).isEqualTo(before);
    }

    @Test
    void failedPersist_changesNeitherLiveTreeNorFileAndLeavesNoTemp(@TempDir Path tmp)
            throws IOException {
        Files.writeString(tmp.resolve("config.yml"), CUSTOM_CONFIG);
        AtomicInteger attempts = new AtomicInteger();
        ConfigFileReplacer failing =
                (source, target) -> {
                    attempts.incrementAndGet();
                    throw new IOException("simulated replacement failure");
                };
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader(), failing);
        String before = Files.readString(tmp.resolve("config.yml"));

        ConfigEditResult result = cfg.edit(new ConfigEdit(combatEnabled(cfg), false));

        assertThat(result).isInstanceOf(ConfigEditResult.Failed.class);
        assertThat(attempts).hasValue(1);
        assertThat(cfg.root().node("modules", "combat", "enabled").getBoolean()).isTrue();
        assertThat(Files.readString(tmp.resolve("config.yml"))).isEqualTo(before);
        try (var files = Files.list(tmp)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactly("config.yml");
        }
    }

    @Test
    void appliedEdit_preservesCommentsAndUnrelatedKeys(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("config.yml"), CUSTOM_CONFIG);
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());

        cfg.edit(new ConfigEdit(combatEnabled(cfg), false));

        String after = Files.readString(tmp.resolve("config.yml"));
        assertThat(after).contains("# Kreiscraft configuration.");
        assertThat(after).contains("# Keep this header comment.");
        assertThat(after).contains("# language selection");
        assertThat(after).contains("language: de");
        assertThat(after).contains("enabled: false");
        assertThat(after).doesNotContain("enabled: true");
    }

    @Test
    void appliedEdit_usesConfiguredReplacementSeam(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("config.yml"), CUSTOM_CONFIG);
        AtomicInteger moves = new AtomicInteger();
        ConfigFileReplacer recording =
                (source, target) -> {
                    moves.incrementAndGet();
                    ConfigFileReplacer.ATOMIC_WHEN_SUPPORTED.replace(source, target);
                };
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader(), recording);

        cfg.edit(new ConfigEdit(combatEnabled(cfg), false));

        assertThat(moves).hasValue(1);
    }

    @Test
    void appliedEdit_isVisibleToAFreshService(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("config.yml"), CUSTOM_CONFIG);
        YamlConfigService first = new YamlConfigService(tmp, getClass().getClassLoader());
        first.edit(new ConfigEdit(combatEnabled(first), false));

        YamlConfigService second = new YamlConfigService(tmp, getClass().getClassLoader());

        assertThat(second.root().node("modules", "combat", "enabled").getBoolean()).isFalse();
    }

    @Test
    void reload_picksUpEditWrittenToDisk(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("config.yml"), CUSTOM_CONFIG);
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());
        Files.writeString(
                tmp.resolve("config.yml"), CUSTOM_CONFIG.replace("enabled: true", "enabled: false"));

        cfg.reload();

        assertThat(cfg.root().node("modules", "combat", "enabled").getBoolean()).isFalse();
    }
}
