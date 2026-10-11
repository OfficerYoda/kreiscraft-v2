package de.kreiscraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Repository-wide assertions for the obsolete module and stale terminology removed by ticket #58.
 *
 * <p>The removed surfaces are: the entire <code>plugin-hider</code> module (package, command,
 * listener, service, data file, permissions, messages, configuration, and bootstrap wiring), the
 * dead <code>core.debug</code> configuration key in maintained code and documentation.
 *
 * <p>Removed files are asserted absent by path, and legitimate Activity Data is asserted retained
 * so that the removal cannot sweep it away.
 */
class RemovedObsoleteModulesTest {

    /** Production and test files for the removed module, which must not survive the removal. */
    private static final List<String> REMOVED_FILES =
            List.of(
                    "src/main/java/de/kreiscraft/modules/pluginhider/PluginHiderModule.java",
                    "src/main/java/de/kreiscraft/modules/pluginhider/PluginHiderService.java",
                    "src/main/java/de/kreiscraft/modules/pluginhider/DefaultPluginHiderService.java",
                    "src/main/java/de/kreiscraft/modules/pluginhider/PluginHiderConfig.java",
                    "src/main/java/de/kreiscraft/modules/pluginhider/PluginHiderData.java",
                    "src/main/java/de/kreiscraft/modules/pluginhider/PluginHiderListener.java",
                    "src/main/java/de/kreiscraft/modules/pluginhider/commands/PluginHiderCommand.java",
                    "src/test/java/de/kreiscraft/modules/pluginhider/PluginHiderConfigTest.java",
                    "src/test/java/de/kreiscraft/modules/pluginhider/DefaultPluginHiderServiceTest.java",
                    "src/test/java/de/kreiscraft/modules/pluginhider/PluginHiderListenerTest.java",
                    "src/test/java/de/kreiscraft/modules/pluginhider/commands/PluginHiderCommandTest.java");

    /**
     * Legitimate Activity Data files unrelated to the removed module. They must remain: removing a
     * module must never delete player or gameplay history it does not own.
     */
    private static final List<String> RETAINED_ACTIVITY_DATA =
            List.of(
                    "src/main/java/de/kreiscraft/shared/preferences/PlayerPreferencesData.java",
                    "src/main/java/de/kreiscraft/modules/status/StatusData.java",
                    "src/main/java/de/kreiscraft/modules/positions/PositionsData.java",
                    "src/main/java/de/kreiscraft/modules/jail/JailData.java",
                    "src/main/java/de/kreiscraft/modules/bedrock/BedrockData.java",
                    "src/main/java/de/kreiscraft/modules/bedrock/BedrockAuthorizationData.java",
                    "src/main/java/de/kreiscraft/modules/anticheat/HackLogData.java");

    // --- structural seam: removed files and configuration -----------------------------------------

    @Test
    void removedModuleSourceAndTestFiles_noLongerExist() {
        for (String removed : REMOVED_FILES) {
            assertThat(Path.of(removed)).as(removed).doesNotExist();
        }
    }

    @Test
    void bundledConfig_hasNoPluginHiderSectionOrDebugKey() {
        ConfigurationNode config = loadResourceYaml("config.yml");
        assertThat(config.node("modules", "plugin-hider").isNull())
                .as("modules.plugin-hider must be absent from the bundled config.yml")
                .isTrue();
        assertThat(config.node("core", "debug").isNull())
                .as("the dead core.debug key must be absent from the bundled config.yml")
                .isTrue();
    }

    @Test
    void pluginYml_declaresNoPluginHiderPermissions() throws IOException {
        String pluginYml =
                Files.readString(Path.of("src/main/resources/plugin.yml"), StandardCharsets.UTF_8);
        assertThat(pluginYml).doesNotContain("pluginhider").doesNotContain("plugin-hider");
        assertThat(pluginYml)
                .as("plugin.yml still declares real permissions")
                .contains("kreiscraft.invsee.use");
    }

    @Test
    void languageTrees_haveNoPluginHiderSection() {
        for (String lang : List.of("de", "en")) {
            ConfigurationNode tree = loadResourceYaml("lang/" + lang + ".yml");
            assertThat(tree.node("plugin-hider").isNull())
                    .as(lang + ".yml must not define the plugin-hider message section")
                    .isTrue();
        }
    }

    @Test
    void bootstrap_noLongerWiresPluginHiderModule() throws IOException {
        String bootstrap =
                Files.readString(
                        Path.of("src/main/java/de/kreiscraft/KreiscraftBootstrap.java"),
                        StandardCharsets.UTF_8);
        assertThat(bootstrap).doesNotContain("pluginhider").doesNotContain("PluginHider");
        assertThat(bootstrap)
                .as("bootstrap still registers real modules")
                .contains("PositionsModule", "WorldMgmtModule");
    }

    // --- structural seam: Activity Data retention -------------------------------------------------

    @Test
    void legitimateActivityData_isRetainedRegardlessOfOwningModule() {
        for (String retained : RETAINED_ACTIVITY_DATA) {
            assertThat(Path.of(retained)).as("%s must be retained", retained).exists();
        }
    }

    // --- helpers ----------------------------------------------------------------------------------

    private static ConfigurationNode loadResourceYaml(String resource) {
        URL url = RemovedObsoleteModulesTest.class.getClassLoader().getResource(resource);
        assertThat(url).as(resource + " must be on the test classpath").isNotNull();
        try {
            return YamlConfigurationLoader.builder().url(url).build().load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + resource, e);
        }
    }

}
