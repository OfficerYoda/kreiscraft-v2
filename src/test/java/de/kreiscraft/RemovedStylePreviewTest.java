package de.kreiscraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.modules.admin.AdminModule;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.gui.GuiRegistry;
import de.kreiscraft.testsupport.CapturingCommandRegistrar;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Repository-wide assertions confirming that the temporary Command Message Style preview surfaces
 * have been removed by ticket #61 (approved selection: Classic).
 *
 * <p>Removed surfaces: the {@code commandstylepreview} command, {@code kreiscraft.admin.style-preview}
 * permission, {@code admin.style-preview.*} message keys, the two preview source files, and their
 * tests.
 *
 * <p>This class names the removed tokens, so it excludes itself from its own text scan.
 */
class RemovedStylePreviewTest {

    private static final String SELF_PATH =
            "src/test/java/de/kreiscraft/RemovedStylePreviewTest.java";

    private static final List<String> FORBIDDEN_TOKENS =
            List.of(
                    "commandstylepreview",
                    "kreiscraft.admin.style-preview",
                    "admin.style-preview",
                    "style-preview.temporary",
                    "style-preview.handoff");

    private static final List<Path> SCAN_ROOTS =
            List.of(Path.of("src/main"), Path.of("docs"), Path.of("src/main/resources"));

    private static final List<String> REMOVED_FILES =
            List.of(
                    "src/main/java/de/kreiscraft/modules/admin/commands/CommandStylePreviewCommand.java",
                    "src/main/java/de/kreiscraft/modules/admin/commands/CommandStylePreviewGui.java",
                    "src/test/java/de/kreiscraft/modules/admin/commands/CommandStylePreviewCommandTest.java",
                    "src/test/java/de/kreiscraft/modules/admin/commands/CommandStylePreviewGuiTest.java");

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void removedPreviewFiles_noLongerExist() {
        for (String removed : REMOVED_FILES) {
            assertThat(Path.of(removed)).as(removed).doesNotExist();
        }
    }

    @Test
    void adminModule_noLongerRegistersStylePreviewCommand() {
        CapturingCommandRegistrar registrar = enableAdminModule();
        assertThat(registrar.names()).doesNotContain("commandstylepreview");
    }

    @Test
    void pluginYml_hasNoStylePreviewPermission() throws IOException {
        String pluginYml =
                Files.readString(Path.of("src/main/resources/plugin.yml"), StandardCharsets.UTF_8);
        assertThat(pluginYml).doesNotContain("style-preview");
    }

    @Test
    void languageTrees_haveNoStylePreviewSection() {
        for (String lang : List.of("de", "en")) {
            ConfigurationNode tree = loadResourceYaml("lang/" + lang + ".yml");
            assertThat(tree.node("admin", "style-preview").isNull())
                    .as(lang + ".yml must not define admin.style-preview")
                    .isTrue();
        }
    }

    @Test
    void shippedSourcesAndDocs_haveNoReferencesToRemovedPreviewSurfaces() throws Exception {
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
                .as("Removed style-preview surfaces still referenced:\n" + String.join("\n", violations))
                .isEmpty();
    }

    private CapturingCommandRegistrar enableAdminModule() {
        CapturingCommandRegistrar registrar = new CapturingCommandRegistrar();
        ServiceRegistry services = new ServiceRegistry();
        services.publish(PlayerPreferencesService.class, new InMemoryPlayerPreferencesService());
        services.publish(
                PlaceholderResolver.class,
                new PlaceholderResolver(server, ZoneId.of("Europe/Berlin"), Clock.systemUTC()));
        services.publish(Gui.class, new Gui(new GuiRegistry()));
        new AdminModule()
                .enable(new ModuleContext(
                        plugin,
                        CommentedConfigurationNode.root(),
                        mock(MessageService.class),
                        services,
                        mock(Scheduler.class),
                        registrar,
                        mock(EventBus.class)));
        return registrar;
    }

    private static ConfigurationNode loadResourceYaml(String resource) {
        URL url = RemovedStylePreviewTest.class.getClassLoader().getResource(resource);
        assertThat(url).as(resource + " must be on the test classpath").isNotNull();
        try {
            return YamlConfigurationLoader.builder().url(url).build().load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + resource, e);
        }
    }

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
        return name.endsWith(".java") || name.endsWith(".yml") || name.endsWith(".md");
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
                int line = (int) content.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
                violations.add(path + ":" + line + " -> " + token);
                index = content.indexOf(token, index + 1);
            }
        }
    }
}
