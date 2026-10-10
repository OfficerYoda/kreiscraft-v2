package de.kreiscraft.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Code–lang parity test.
 *
 * <p>Scans every {@code *.java} file under {@code src/main/java/de/kreiscraft/} and extracts the
 * string-literal key from every {@code messages.send(...)}, {@code messages.broadcast(...)},
 * {@code messages.render(...)}, and {@code messages.renderFor(...)} call. Each extracted key is then
 * resolved against both {@code lang/de.yml} and {@code lang/en.yml} (loaded from the test classpath).
 * The test fails if any key does not resolve to a non-null string leaf in either file, reporting the
 * source file+location and the missing lang file(s).
 *
 * <p>Keys passed via variables (non-literal arguments) are not captured by the regex and are
 * therefore not checked — this is intentional and documented as a known limitation.
 */
class MessageKeyReferenceTest {

    /**
     * Regex to extract the first string-literal argument from a {@code
     * messages.send/broadcast/render/renderFor} call.
     *
     * <p>Uses {@code [^";]*} (not {@code [^"]*}) to prevent the pattern from spanning across
     * statement boundaries (a {@code ;} terminates the Java statement). Without this restriction the
     * pattern could start at a {@code messages.send(player, keyVariable)} call, skip past the
     * closing {@code )} and {@code ;}, and spuriously match the next string literal in the file as
     * though it were the key argument.
     *
     * <p>The pattern uses {@link Pattern#DOTALL} so that {@code [^";]*} can span newlines (common
     * when the key literal is on the line after the method name in formatted code).
     *
     * <p>Capture group 2 contains the key.
     */
    private static final Pattern KEY_PATTERN =
            Pattern.compile(
                    "messages\\.(send|broadcast|renderFor|render)\\s*\\([^\";]*\"([a-z][a-z0-9.\\-]+)\"",
                    Pattern.DOTALL);

    /** Root of the main Java sources to scan. */
    private static final Path SRC_ROOT =
            Path.of("src/main/java/de/kreiscraft");

    @Test
    void allMessageKeyReferencesResolveInBothLangFiles() throws IOException {
        // --- load lang files from the test classpath (same JARs as production) ---
        URL deUrl = getClass().getClassLoader().getResource("lang/de.yml");
        URL enUrl = getClass().getClassLoader().getResource("lang/en.yml");
        assertThat(deUrl).as("lang/de.yml must be on the test classpath").isNotNull();
        assertThat(enUrl).as("lang/en.yml must be on the test classpath").isNotNull();

        ConfigurationNode deRoot =
                YamlConfigurationLoader.builder().url(deUrl).build().load();
        ConfigurationNode enRoot =
                YamlConfigurationLoader.builder().url(enUrl).build().load();

        // --- scan source files and collect key references ---
        // Map: key → list of "file:line" descriptors
        Map<String, List<String>> keyOccurrences = new LinkedHashMap<>();

        try (Stream<Path> files = Files.walk(SRC_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .forEach(javaFile -> {
                        try {
                            String content = Files.readString(javaFile);
                            Matcher m = KEY_PATTERN.matcher(content);
                            while (m.find()) {
                                String key = m.group(2);
                                // Build a short "file:approx-line" descriptor for the report.
                                String upToMatch =
                                        content.substring(0, m.start(2));
                                int line =
                                        (int) upToMatch.chars().filter(c -> c == '\n').count() + 1;
                                String relPath = SRC_ROOT.getParent()
                                        .getParent()
                                        .relativize(javaFile)
                                        .toString();
                                String location = relPath + ":" + line;
                                keyOccurrences
                                        .computeIfAbsent(key, k -> new ArrayList<>())
                                        .add(location);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read " + javaFile, e);
                        }
                    });
        }

        // --- verify each key against both lang files ---
        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : keyOccurrences.entrySet()) {
            String key = entry.getKey();
            List<String> locations = entry.getValue();
            String[] parts = key.split("\\.");
            Object[] pathObjs = Arrays.copyOf(parts, parts.length, Object[].class);

            boolean missingDe = deRoot.node(pathObjs).getString() == null;
            boolean missingEn = enRoot.node(pathObjs).getString() == null;

            if (missingDe || missingEn) {
                String missingIn =
                        (missingDe && missingEn) ? "de.yml + en.yml"
                                : missingDe ? "de.yml"
                                        : "en.yml";
                for (String loc : locations) {
                    failures.add(
                            String.format(
                                    "  key \"%s\" (used at %s) is missing from %s",
                                    key, loc, missingIn));
                }
            }
        }

        assertThat(failures)
                .as(
                        "Broken message-key references found in source code:\n"
                                + String.join("\n", failures))
                .isEmpty();
    }
}
