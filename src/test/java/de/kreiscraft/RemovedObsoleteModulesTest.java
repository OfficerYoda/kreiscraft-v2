package de.kreiscraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Repository-wide assertions for the obsolete module and stale terminology removed by ticket #58.
 *
 * <p>The removed surfaces are: the entire <code>plugin-hider</code> module (package, command,
 * listener, service, data file, permissions, messages, configuration, and bootstrap wiring), the
 * dead <code>core.debug</code> configuration key, and every <code>RusticPrism</code> reference in
 * maintained code and documentation.
 *
 * <p>The primary seam is a text scan of repository-tracked production sources, resources,
 * documentation, and the README. It deliberately uses <code>git ls-files</code> so that untracked or
 * git-ignored local files cannot make the assertion non-hermetic. This guard names the forbidden
 * tokens, so it excludes itself from the scan. Removed files are asserted absent by path, and
 * legitimate Activity Data is asserted retained so that the removal cannot sweep it away.
 */
class RemovedObsoleteModulesTest {

    /** Tokens whose presence anywhere in a maintained tracked file means removal was incomplete. */
    private static final List<String> FORBIDDEN_TOKENS =
            List.of(
                    "pluginhider",
                    "plugin-hider",
                    "plugin hider",
                    "rusticprism",
                    "rustic prism",
                    "core.debug");

    /** Repository paths scanned for lingering references. */
    private static final List<Path> SCAN_ROOTS =
            List.of(Path.of("src"), Path.of("docs"), Path.of("docs-site"), Path.of("README.md"));

    /** This guard names the removed tokens, so it is excluded from its own text scan. */
    private static final String SELF_PATH =
            "src/test/java/de/kreiscraft/RemovedObsoleteModulesTest.java";

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

    // --- repository-wide text seam ----------------------------------------------------------------

    @Test
    void maintainedTrackedFiles_haveNoReferencesToRemovedModuleOrStaleTerminology() throws Exception {
        List<String> violations = new ArrayList<>();

        for (String tracked : trackedFilesUnder(SCAN_ROOTS)) {
            if (SELF_PATH.equals(tracked)) {
                continue;
            }
            Path path = Path.of(tracked);
            if (isScannable(path) && Files.isRegularFile(path)) {
                collectViolations(path, violations);
            }
        }

        assertThat(violations)
                .as(
                        "Obsolete modules or stale terminology are still referenced:\n"
                                + String.join("\n", violations))
                .isEmpty();
    }

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

    /**
     * Lists the repository-tracked files under the given roots. The scan deliberately observes only
     * content that is part of the repository: untracked or git-ignored local files (such as a
     * developer's private planning notes) must never influence the assertion, or the build would be
     * non-hermetic. Falls back to a plain filesystem walk only when no Git checkout is present.
     */
    private static List<String> trackedFilesUnder(List<Path> roots) throws Exception {
        if (!Files.exists(Path.of(".git"))) {
            return filesystemFilesUnder(roots);
        }

        List<String> command = new ArrayList<>(List.of("git", "ls-files", "-z", "--"));
        roots.stream().map(Path::toString).forEach(command::add);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("git ls-files timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("git ls-files failed: " + output);
        }
        List<String> tracked = new ArrayList<>();
        for (String entry : output.split("\0")) {
            if (!entry.isBlank()) {
                tracked.add(entry);
            }
        }
        return tracked;
    }

    private static List<String> filesystemFilesUnder(List<Path> roots) throws IOException {
        List<String> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .sorted()
                        .map(Path::toString)
                        .forEach(files::add);
            }
        }
        return files;
    }

    private static boolean isScannable(Path path) {
        String name = path.toString();
        return name.endsWith(".java")
                || name.endsWith(".yml")
                || name.endsWith(".md")
                || name.endsWith(".ts")
                || name.endsWith(".mjs");
    }

    private static void collectViolations(Path path, List<String> violations) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + path, e);
        }
        for (String token : FORBIDDEN_TOKENS) {
            int index = content.indexOf(token);
            while (index >= 0) {
                int line =
                        (int) content.substring(0, index).chars().filter(c -> c == '\n').count()
                                + 1;
                violations.add(path + ":" + line + " -> " + token);
                index = content.indexOf(token, index + 1);
            }
        }
    }
}
