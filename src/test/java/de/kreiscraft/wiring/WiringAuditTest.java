package de.kreiscraft.wiring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Regression guard: every wiring spec in {@link #WIRING_DIR} must match the current state of the
 * shared resource files. If a future PR silently removes a config key, lang entry, permission node,
 * or bootstrap registration, this test catches it.
 *
 * <p>All paths are defined as constants here; update {@link #WIRING_DIR} if the archive is ever
 * relocated again.
 */
class WiringAuditTest {

    // ---- Paths (update WIRING_DIR if the archive directory moves) ---------------

    private static final Path WIRING_DIR = Path.of("docs/wiring-history");
    private static final Path CONFIG_YML = Path.of("src/main/resources/config.yml");
    private static final Path LANG_DE = Path.of("src/main/resources/lang/de.yml");
    private static final Path LANG_EN = Path.of("src/main/resources/lang/en.yml");
    private static final Path PLUGIN_YML = Path.of("src/main/resources/plugin.yml");
    private static final Path BOOTSTRAP =
            Path.of("src/main/java/de/kreiscraft/KreiscraftBootstrap.java");

    // ---- Parameterized source ---------------------------------------------------

    static Stream<Path> wiringFiles() throws IOException {
        return Files.list(WIRING_DIR)
                .filter(p -> p.toString().endsWith(".md"))
                .filter(p -> !p.getFileName().toString().equalsIgnoreCase("readme.md"))
                .sorted();
    }

    // ---- Main audit test --------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("wiringFiles")
    void wiringIsApplied(Path wiringFile) throws IOException {
        String md = Files.readString(wiringFile);

        String configSection = section(md, "config.yml addition");
        String langDeSection = section(md, "lang/de.yml addition");
        String langEnSection = section(md, "lang/en.yml addition");
        String permSection = section(md, "plugin.yml permissions");
        String bootstrapSection = section(md, "KreiscraftBootstrap registration");

        Set<String> configLines = trimmedLineSet(Files.readString(CONFIG_YML));
        Set<String> langDeLines = trimmedLineSet(Files.readString(LANG_DE));
        Set<String> langEnLines = trimmedLineSet(Files.readString(LANG_EN));
        Set<String> pluginLines = trimmedLineSet(Files.readString(PLUGIN_YML));
        Set<String> bootstrapLines = trimmedLineSet(Files.readString(BOOTSTRAP));

        String filename = wiringFile.getFileName().toString();

        // config.yml: every non-blank, non-comment line must appear (trimmed)
        if (!isNone(configSection)) {
            for (String line : contentLines(configSection)) {
                if (!line.strip().startsWith("#")) {
                    assertThat(configLines)
                            .as("config.yml missing line [%s] declared in %s", line.strip(), filename)
                            .contains(line.strip());
                }
            }
        }

        // lang/de.yml: every non-blank line must appear (trimmed)
        if (!isNone(langDeSection)) {
            for (String line : contentLines(langDeSection)) {
                assertThat(langDeLines)
                        .as(
                                "lang/de.yml missing line [%s] declared in %s",
                                line.strip(), filename)
                        .contains(line.strip());
            }
        }

        // lang/en.yml: every non-blank line must appear (trimmed)
        if (!isNone(langEnSection)) {
            for (String line : contentLines(langEnSection)) {
                assertThat(langEnLines)
                        .as(
                                "lang/en.yml missing line [%s] declared in %s",
                                line.strip(), filename)
                        .contains(line.strip());
            }
        }

        // plugin.yml: every permission node key line (trimmed, starting with "kreiscraft.")
        if (!isNone(permSection)) {
            for (String line : contentLines(permSection)) {
                String stripped = line.strip();
                if (stripped.startsWith("kreiscraft.")) {
                    assertThat(pluginLines)
                            .as(
                                    "plugin.yml missing permission key [%s] declared in %s",
                                    stripped, filename)
                            .contains(stripped);
                }
            }
        }

        // KreiscraftBootstrap: the modules.register(…) line must appear (trimmed)
        if (!isNone(bootstrapSection)) {
            String registerLine =
                    contentLines(bootstrapSection).stream()
                            .map(String::strip)
                            .filter(l -> l.startsWith("modules.register("))
                            .findFirst()
                            .orElseThrow(
                                    () -> new AssertionError(
                                            "No modules.register line found in bootstrap"
                                                    + " section of "
                                                    + filename));
            assertThat(bootstrapLines)
                    .as(
                            "KreiscraftBootstrap.java missing registration [%s] declared in %s",
                            registerLine, filename)
                    .contains(registerLine);
        }
    }

    // ---- Global parity test ----------------------------------------------------

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

    // ---- Helpers ----------------------------------------------------------------

    /**
     * Returns the body of the first {@code ## sectionName} section in the markdown, trimmed. If the
     * section is absent, returns {@code "(none)"}.
     */
    private static String section(String markdown, String sectionName) {
        Pattern headerPat = Pattern.compile("(?m)^## (.+)$");
        Matcher m = headerPat.matcher(markdown);
        List<int[]> spans = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[] {m.start(), m.end()});
            names.add(m.group(1).trim());
        }
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equals(sectionName)) {
                int bodyStart = spans.get(i)[1];
                int bodyEnd = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : markdown.length();
                return markdown.substring(bodyStart, bodyEnd).trim();
            }
        }
        return "(none)";
    }

    /**
     * Extracts non-blank lines from a section body. If the body contains a fenced code block (
     * {@code ```...```}), the lines inside the fence are used; otherwise all non-blank lines in the
     * body are used.
     */
    private static List<String> contentLines(String sectionBody) {
        Pattern fencePat = Pattern.compile("```(?:\\w+)?\\n([\\s\\S]*?)```");
        Matcher fm = fencePat.matcher(sectionBody);
        String source = fm.find() ? fm.group(1) : sectionBody;
        List<String> result = new ArrayList<>();
        for (String line : source.split("\n")) {
            if (!line.isBlank()) {
                result.add(line);
            }
        }
        return result;
    }

    /** Returns {@code true} if the section body represents an absent/empty section. */
    private static boolean isNone(String sectionBody) {
        return sectionBody.trim().equals("(none)");
    }

    /** Returns a set of trimmed, non-blank lines from the given file content. */
    private static Set<String> trimmedLineSet(String content) {
        Set<String> set = new LinkedHashSet<>();
        for (String line : content.split("\n")) {
            String stripped = line.strip();
            if (!stripped.isEmpty()) {
                set.add(stripped);
            }
        }
        return set;
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
