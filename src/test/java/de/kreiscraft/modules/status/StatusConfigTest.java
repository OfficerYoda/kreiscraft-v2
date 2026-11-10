package de.kreiscraft.modules.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class StatusConfigTest {

    @Test
    void emptyYamlMap_parsesToStatusConfig() throws Exception {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader("status: {}")))
                .build();
        ConfigurationNode root = loader.load();
        StatusConfig config = root.node("status").get(StatusConfig.class);
        assertThat(config).isNotNull();
        assertThat(config).isEqualTo(new StatusConfig());
        assertThat(config.joinFormat())
                .isEqualTo("<kc-prefix><gray> » <player-prefix><name-colored><player-suffix>");
        assertThat(config.leaveFormat())
                .isEqualTo("<kc-prefix><gray> « <player-prefix><name-colored><player-suffix>");
    }

    @Test
    void roundTrip_preservesFormats() throws Exception {
        StatusConfig original = new StatusConfig("Hello <name-colored>", "Bye <name-colored>");
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader("status: {}")))
                .build();
        ConfigurationNode root = loader.load();
        root.node("status").set(StatusConfig.class, original);
        StatusConfig loaded = root.node("status").get(StatusConfig.class);
        assertThat(loaded).isEqualTo(original);
    }
}
