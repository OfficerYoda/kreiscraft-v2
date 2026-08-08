package de.kreiscraft.lang;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Regression guard for the two language files: {@code lang/de.yml} and {@code lang/en.yml} must
 * expose exactly the same set of leaf key paths. Adding a translation to one file without the
 * other is a bug this test catches on every build.
 */
class LangParityTest {

    private static final Path LANG_DE = Path.of("src/main/resources/lang/de.yml");
    private static final Path LANG_EN = Path.of("src/main/resources/lang/en.yml");

    @Test
    void deAndEnHaveIdenticalKeyPaths() throws Exception {
        Set<String> de =
                collectLeafPaths(
                        YamlConfigurationLoader.builder().path(LANG_DE).build().load());
        Set<String> en =
                collectLeafPaths(
                        YamlConfigurationLoader.builder().path(LANG_EN).build().load());
        assertThat(de).isEqualTo(en);
    }

    /** Recursively collects dotted leaf-key paths from a Configurate node tree. */
    private static Set<String> collectLeafPaths(ConfigurationNode root) {
        Set<String> paths = new LinkedHashSet<>();
        walkNode(root, new ArrayList<>(), paths);
        return paths;
    }

    private static void walkNode(
            ConfigurationNode node, List<String> currentPath, Set<String> paths) {
        if (node.isMap()) {
            for (var entry : node.childrenMap().entrySet()) {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add(entry.getKey().toString());
                walkNode(entry.getValue(), newPath, paths);
            }
        } else {
            if (!currentPath.isEmpty()) {
                paths.add(String.join(".", currentPath));
            }
        }
    }
}
