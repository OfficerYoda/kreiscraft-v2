package de.kreiscraft.modules.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.SerializationException;

class BedrockConfigTest {

    private static ConfigurationOptions opts() {
        return ConfigurationOptions.defaults()
                .serializers(KreiscraftSerializers.builder().build());
    }

    @Test
    void defaults_whenNodeIsEmpty() throws SerializationException {
        BasicConfigurationNode node = BasicConfigurationNode.root(opts());
        BedrockConfig cfg = node.get(BedrockConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.prefix()).isEqualTo(".");
    }

    @Test
    void prefix_parsesFromNode() throws SerializationException {
        BasicConfigurationNode node = BasicConfigurationNode.root(opts());
        node.node("prefix").set("_");
        BedrockConfig cfg = node.get(BedrockConfig.class);
        assertThat(cfg.prefix()).isEqualTo("_");
    }

    @Test
    void prefix_roundTrips() throws SerializationException {
        BasicConfigurationNode node = BasicConfigurationNode.root(opts());
        node.set(BedrockConfig.class, new BedrockConfig("."));
        BedrockConfig loaded = node.get(BedrockConfig.class);
        assertThat(loaded.prefix()).isEqualTo(".");
    }
}
