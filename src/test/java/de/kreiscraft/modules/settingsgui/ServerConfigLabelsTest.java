package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ServerConfigSchema;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Language-coverage guard for the Server Configuration GUI.
 *
 * <p>{@link ServerConfigGui} builds a field's label key from its path and a category's label key
 * from its id, so those keys are not string literals the {@code MessageKeyReferenceTest} can see.
 * This test therefore enumerates the real schema and asserts that every category and every exposed
 * field resolves to a non-null label in both {@code de.yml} and {@code en.yml}, keeping the GUI and
 * the language trees from drifting apart.
 */
class ServerConfigLabelsTest {

    @Test
    void everyCategoryAndExposedFieldHasALabelInBothLanguages() throws Exception {
        ConfigurationNode de = load("lang/de.yml");
        ConfigurationNode en = load("lang/en.yml");
        URL configUrl = getClass().getClassLoader().getResource("config.yml");
        assertThat(configUrl).as("config.yml must be on the test classpath").isNotNull();
        ConfigurationNode config = YamlConfigurationLoader.builder().url(configUrl).build().load();
        ServerConfigSchema schema = ServerConfigSchema.fromRoot(config);

        List<String> failures = new ArrayList<>();
        for (String category : ServerConfigGui.categories(schema)) {
            check(failures, de, en, ServerConfigGui.categoryKey(category));
        }
        for (ConfigField field : schema.guiExposed()) {
            check(failures, de, en, ServerConfigGui.fieldKey(field));
        }

        assertThat(failures)
                .as("Missing Server Configuration GUI labels:\n" + String.join("\n", failures))
                .isEmpty();
    }

    private static void check(
            List<String> failures, ConfigurationNode de, ConfigurationNode en, String key) {
        Object[] path = Arrays.copyOf(key.split("\\."), key.split("\\.").length, Object[].class);
        if (de.node(path).getString() == null) {
            failures.add("  " + key + " missing from de.yml");
        }
        if (en.node(path).getString() == null) {
            failures.add("  " + key + " missing from en.yml");
        }
    }

    private static ConfigurationNode load(String resource) throws Exception {
        URL url = ServerConfigLabelsTest.class.getClassLoader().getResource(resource);
        assertThat(url).as(resource + " must be on the test classpath").isNotNull();
        return YamlConfigurationLoader.builder().url(url).build().load();
    }
}
