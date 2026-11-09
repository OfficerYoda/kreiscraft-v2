package de.kreiscraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleRegistry;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.CommentedConfigurationNode;

class KreiscraftBootstrapTest {

    @Test
    void startupRejectsInvalidCrownBeforePublishingConfiguration(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("config.yml"), """
                modules:
                  shattered-crown:
                    enabled: false
                    arena-radius: 20
                """);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tmp.toFile());

        assertThatThrownBy(() -> new KreiscraftBootstrap(plugin).start())
                .isInstanceOf(IOException.class).hasMessageContaining("geometry");
        assertThat(Files.exists(tmp.resolve("data"))).isFalse();
    }

    /** Discover production implementations so new modules are covered without updating a fixture. */
    private static List<Module> allModules() throws Exception {
        Path classes = Path.of(Module.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Module> modules = new ArrayList<>();
        try (var files = Files.walk(classes.resolve("de/kreiscraft/modules"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                String relative = classes.relativize(file).toString();
                String className = relative.substring(0, relative.length() - ".class".length())
                        .replace(file.getFileSystem().getSeparator(), ".");
                Class<?> type = Class.forName(className, false, Module.class.getClassLoader());
                if (Module.class.isAssignableFrom(type) && !type.isInterface()
                        && !Modifier.isAbstract(type.getModifiers())) {
                    modules.add(type.asSubclass(Module.class).getConstructor().newInstance());
                }
            }
        }
        assertThat(modules).as("discovered production modules").isNotEmpty();
        return modules;
    }

    @Test
    void moduleRegistry_registersAllModulesWithoutDuplicates() throws Exception {
        List<Module> modules = allModules();
        ModuleRegistry registry =
                new ModuleRegistry(
                        m -> {
                            throw new UnsupportedOperationException(
                                    "enable() not invoked in this smoke test");
                        },
                        CommentedConfigurationNode.root(),
                        Logger.getLogger("test"));

        for (Module m : modules) {
            registry.register(m);
        }

        assertThat(registry.registeredIds())
                .containsExactlyInAnyOrderElementsOf(modules.stream().map(Module::id).toList());
    }

    @Test
    void everyRegisteredModule_hasKebabCaseId() throws Exception {
        // Every module id must be non-blank and match the kebab-case pattern enforced by the
        // module template. This also verifies that every module has a working no-arg constructor.
        for (Module m : allModules()) {
            assertThat(m.id())
                    .as("module id for %s", m.getClass().getSimpleName())
                    .isNotBlank()
                    .matches("^[a-z]+(-[a-z]+)*$");
        }
    }
}
