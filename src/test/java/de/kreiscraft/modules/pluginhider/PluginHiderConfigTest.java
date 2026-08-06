package de.kreiscraft.modules.pluginhider;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class PluginHiderConfigTest {

    private static ConfigurationNode parseYaml(String yaml) throws Exception {
        return YamlConfigurationLoader.builder()
                .defaultOptions(ConfigurationOptions.defaults()
                        .serializers(KreiscraftSerializers.builder().build()))
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .build()
                .load();
    }

    @Test
    void config_parsesDefaultAllowedCommands() throws Exception {
        ConfigurationNode node = parseYaml(
                "default-allowed-commands:\n"
                        + "  - help\n"
                        + "  - spawn\n");
        PluginHiderConfig config = node.get(PluginHiderConfig.class);

        assertThat(config).isNotNull();
        assertThat(config.defaultAllowedCommands()).containsExactly("help", "spawn");
    }

    @Test
    void config_returnsEmptyListWhenKeyAbsent() throws Exception {
        // Configurate returns [] (not null) for an absent list key.
        // The module seeds from config; an empty list means "hide everything (except /help)".
        ConfigurationNode node = parseYaml("other-key: irrelevant\n");
        PluginHiderConfig config = node.get(PluginHiderConfig.class);

        assertThat(config).isNotNull();
        assertThat(config.defaultAllowedCommands()).isEmpty();
    }
}
