package de.kreiscraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.modules.admin.AdminModule;
import de.kreiscraft.modules.updatemode.UpdateModeService;
import de.kreiscraft.modules.updatemode.commands.UpdateModeCommand;
import de.kreiscraft.modules.worldmgmt.WorldMgmtModule;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.gui.GuiRegistry;
import de.kreiscraft.testsupport.CapturingCommandRegistrar;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
import org.bukkit.command.CommandSender;
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
 * Repository-wide assertions for the superseded command surfaces removed by ticket #57.
 *
 * <p>The removed surfaces are: <code>/i</code> (item shorthand), the gamemode commands
 * <code>/gm</code>/<code>/kreiscraftgamemode</code> and their aliases, <code>/kreiscraftweather</code>
 * (<code>kcweather</code>), <code>/setworldspawn</code> (<code>sws</code>), and the German
 * <code>/updatemode an|aus</code> sub-commands.
 *
 * <p>Two seams are used: the module command-registration boundary (observed with a capturing
 * {@link CapturingCommandRegistrar}) and a text scan of repository-tracked production sources,
 * resources, documentation, and the README. The scan deliberately omits the test tree, where this
 * class necessarily names the removed surfaces; removed test files are asserted absent by path
 * instead. Only tracked content is scanned so that untracked or git-ignored local files cannot make
 * the assertion non-hermetic.
 */
class RemovedCommandSurfacesTest {

    /** Tokens whose presence anywhere in shipped sources/docs means a surface was only partly removed. */
    private static final List<String> FORBIDDEN_TOKENS =
            List.of(
                    "kreiscraftgamemode",
                    "kreiscraftgm",
                    "kcgamemode",
                    "kcgm",
                    "kreiscraftweather",
                    "kcweather",
                    "setworldspawn",
                    "kreiscraft.admin.gamemode",
                    "kreiscraft.admin.weather",
                    "kreiscraft.worldmgmt.setworldspawn",
                    "admin.gamemode-set",
                    "admin.weather-set",
                    "world-mgmt.spawn-set");

    /** Repository paths scanned for lingering references. */
    private static final List<Path> SCAN_ROOTS =
            List.of(Path.of("src/main"), Path.of("docs"), Path.of("README.md"));

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

    // --- module registration seam -----------------------------------------------------------------

    @Test
    void adminModule_noLongerRegistersGamemodeCommandsOrAliases() {
        CapturingCommandRegistrar registrar = enableAdminModule();

        assertThat(registrar.names()).doesNotContain("gm", "kreiscraftgamemode");
        assertThat(registrar.aliases()).doesNotContain("kreiscraftgm", "kcgamemode", "kcgm");
    }

    @Test
    void adminModule_noLongerRegistersCustomWeatherCommandOrAlias() {
        CapturingCommandRegistrar registrar = enableAdminModule();

        assertThat(registrar.names()).doesNotContain("kreiscraftweather");
        assertThat(registrar.aliases()).doesNotContain("kcweather");
    }

    @Test
    void adminModule_noLongerRegistersItemShorthandAlias() {
        CapturingCommandRegistrar registrar = enableAdminModule();

        assertThat(registrar.names()).contains("item");
        assertThat(registrar.aliases()).doesNotContain("i");
    }

    @Test
    void worldMgmtModule_noLongerRegistersSetWorldSpawnCommandOrAlias() {
        CapturingCommandRegistrar registrar = enableWorldMgmtModule();

        assertThat(registrar.names()).doesNotContain("setworldspawn");
        assertThat(registrar.aliases()).doesNotContain("sws");
        assertThat(registrar.names()).contains("world", "portal", "dimension");
    }

    @Test
    void updateModeCommand_onlyExposesOnOffStatus() {
        var node =
                UpdateModeCommand.build(
                        mock(UpdateModeService.class), mock(MessageService.class));

        assertThat(node.getChildren().stream().map(CommandNode::getName))
                .containsExactlyInAnyOrder("on", "off", "status");
    }

    @Test
    void updateModeCommand_rejectsGermanAliases() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(
                        UpdateModeCommand.build(
                                mock(UpdateModeService.class), mock(MessageService.class)));
        CommandSourceStack source = mock(CommandSourceStack.class);
        CommandSender sender = mock(CommandSender.class);
        when(source.getSender()).thenReturn(sender);
        when(sender.hasPermission("kreiscraft.updatemode.admin")).thenReturn(true);

        assertThatThrownBy(() -> dispatcher.execute("updatemode an", source))
                .isInstanceOf(CommandSyntaxException.class);
        assertThatThrownBy(() -> dispatcher.execute("updatemode aus", source))
                .isInstanceOf(CommandSyntaxException.class);
    }

    // --- repository-wide text seam ----------------------------------------------------------------

    @Test
    void shippedSourcesAndDocs_haveNoReferencesToRemovedSurfaces() throws Exception {
        List<String> violations = new ArrayList<>();

        for (String tracked : trackedFilesUnder(SCAN_ROOTS)) {
            Path path = Path.of(tracked);
            if (isScannable(path) && Files.isRegularFile(path)) {
                collectViolations(path, violations);
            }
        }

        assertThat(violations)
                .as(
                        "Removed command surfaces are still referenced:\n"
                                + String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void removedMessageKeys_areAbsentFromBothLanguages() throws Exception {
        URL deUrl = getClass().getClassLoader().getResource("lang/de.yml");
        URL enUrl = getClass().getClassLoader().getResource("lang/en.yml");
        assertThat(deUrl).as("lang/de.yml must be on the test classpath").isNotNull();
        assertThat(enUrl).as("lang/en.yml must be on the test classpath").isNotNull();
        ConfigurationNode de = YamlConfigurationLoader.builder().url(deUrl).build().load();
        ConfigurationNode en = YamlConfigurationLoader.builder().url(enUrl).build().load();

        for (String key : List.of("admin.gamemode-set", "admin.weather-set", "world-mgmt.spawn-set")) {
            Object[] path = key.split("\\.");
            assertThat(de.node(path).getString()).as("de.yml still defines " + key).isNull();
            assertThat(en.node(path).getString()).as("en.yml still defines " + key).isNull();
        }
    }

    @Test
    void removedCommandClassesAndTestsNoLongerExist() {
        assertThat(Path.of("src/main/java/de/kreiscraft/modules/admin/commands/KreiscraftGamemodeCommand.java"))
                .doesNotExist();
        assertThat(Path.of("src/main/java/de/kreiscraft/modules/admin/commands/GamemodeCommand.java"))
                .doesNotExist();
        assertThat(Path.of("src/main/java/de/kreiscraft/modules/admin/commands/WeatherCommand.java"))
                .doesNotExist();
        assertThat(Path.of("src/main/java/de/kreiscraft/modules/worldmgmt/commands/SetWorldSpawnCommand.java"))
                .doesNotExist();
        assertThat(Path.of("src/test/java/de/kreiscraft/modules/admin/commands/KreiscraftGamemodeCommandTest.java"))
                .doesNotExist();
        assertThat(Path.of("src/test/java/de/kreiscraft/modules/admin/commands/GamemodeCommandTest.java"))
                .doesNotExist();
        assertThat(Path.of("src/test/java/de/kreiscraft/modules/admin/commands/WeatherCommandTest.java"))
                .doesNotExist();
        assertThat(Path.of("src/test/java/de/kreiscraft/modules/worldmgmt/commands/SetWorldSpawnCommandTest.java"))
                .doesNotExist();
    }

    // --- helpers ----------------------------------------------------------------------------------

    private CapturingCommandRegistrar enableAdminModule() {
        CapturingCommandRegistrar registrar = new CapturingCommandRegistrar();
        ServiceRegistry services = new ServiceRegistry();
        services.publish(PlayerPreferencesService.class, new InMemoryPlayerPreferencesService());
        services.publish(
                PlaceholderResolver.class,
                new PlaceholderResolver(server, ZoneId.of("Europe/Berlin"), Clock.systemUTC()));
        services.publish(Gui.class, new Gui(new GuiRegistry()));
        new AdminModule()
                .enable(
                        new ModuleContext(
                                plugin,
                                CommentedConfigurationNode.root(),
                                mock(MessageService.class),
                                services,
                                mock(Scheduler.class),
                                registrar,
                                mock(EventBus.class)));
        return registrar;
    }

    private CapturingCommandRegistrar enableWorldMgmtModule() {
        CapturingCommandRegistrar registrar = new CapturingCommandRegistrar();
        ServiceRegistry services = new ServiceRegistry();
        services.publish(PaperPlatform.class, new PaperPlatform(plugin, server));
        services.publish(ConfigService.class, mock(ConfigService.class));
        new WorldMgmtModule()
                .enable(
                        new ModuleContext(
                                plugin,
                                CommentedConfigurationNode.root(),
                                mock(MessageService.class),
                                services,
                                mock(Scheduler.class),
                                registrar,
                                mock(EventBus.class)));
        return registrar;
    }

    /**
     * Lists the repository-tracked files under the given roots. The scan deliberately observes only
     * content that is part of the repository: untracked or git-ignored local files (such as a
     * developer's private planning notes) must never influence the assertion, or the build would be
     * non-hermetic. Falls back to a plain filesystem walk when the working directory is not a Git
     * checkout, so tracking information is unavailable.
     */
    private static List<String> trackedFilesUnder(List<Path> roots) throws Exception {
        if (!Files.isDirectory(Path.of(".git"))) {
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
                int line =
                        (int) content.substring(0, index).chars().filter(c -> c == '\n').count()
                                + 1;
                violations.add(path + ":" + line + " -> " + token);
                index = content.indexOf(token, index + 1);
            }
        }
    }
}
