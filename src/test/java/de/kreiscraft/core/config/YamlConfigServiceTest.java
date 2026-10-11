package de.kreiscraft.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    void bundledConfig_isCommentFree(@TempDir Path tmp) throws IOException {
        new YamlConfigService(tmp, getClass().getClassLoader());

        assertThat(Files.readString(tmp.resolve("config.yml")))
                .doesNotContainPattern("(?m)(?:^|\\s)#");
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

    /** Modules that ship active in the bundled config so a clean server works out of the box. */
    private static final List<String> DEFAULT_ENABLED = List.of(
            "admin", "api", "ai-chat", "anti-cheat", "bedrock", "chat-format", "clickthrough",
            "combat", "effects", "glow", "invsee", "jail", "reboot", "settings-gui",
            "sign-color", "spawn", "start-sequence", "status", "tablist", "update-mode",
            "wither", "world-mgmt");

    /**
     * Modules that ship inactive by default. Their implementations remain available for explicit
     * opt-in via {@code modules.<id>.enabled: true}; see the Module Catalog for the rationale.
     */
    private static final List<String> DEFAULT_DISABLED = List.of(
            "firework", "positions");

    @Test
    void everyModuleSection_hasEnabledKey(@TempDir Path tmp) throws IOException {
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());
        ConfigurationNode modules = cfg.root().node("modules");

        for (String id : DEFAULT_ENABLED) {
            assertModuleEnabled(modules, id, true);
        }
        for (String id : DEFAULT_DISABLED) {
            assertModuleEnabled(modules, id, false);
        }
    }

    /**
     * Guards the activation policy against drift: every module section present in the bundled
     * config must be classified as default-on or default-off, so adding a module without deciding
     * its default fails the build.
     */
    @Test
    void everyModuleSection_isClassifiedAsDefaultOnOrDefaultOff(@TempDir Path tmp)
            throws IOException {
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());
        ConfigurationNode modules = cfg.root().node("modules");

        List<String> classified = new ArrayList<>(DEFAULT_ENABLED);
        classified.addAll(DEFAULT_DISABLED);
        List<String> present = new ArrayList<>();
        for (Object key : modules.childrenMap().keySet()) {
            present.add(String.valueOf(key));
        }

        assertThat(present)
                .as("every config.yml module section must be classified default-on or default-off")
                .containsExactlyInAnyOrderElementsOf(classified);
    }

    @Test
    void defaultDisabledModules_areDisjointFromDefaultEnabled() {
        assertThat(DEFAULT_ENABLED).doesNotContainAnyElementsOf(DEFAULT_DISABLED);
        assertThat(DEFAULT_DISABLED).doesNotContainAnyElementsOf(DEFAULT_ENABLED);
    }

    private static void assertModuleEnabled(ConfigurationNode modules, String id, boolean enabled) {
        assertThat(modules.node(id).isNull())
                .as("modules.%s should be present in config.yml", id)
                .isFalse();
        assertThat(modules.node(id, "enabled").getBoolean())
                .as("modules.%s.enabled should default to %s", id, enabled)
                .isEqualTo(enabled);
    }
}
