package de.kreiscraft.modules.firework;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.SerializationException;

class FireworkConfigTest {

    @Test
    void defaultNamespaceKey_whenFieldMissing() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root(ConfigurationOptions.defaults());
        node.node("custom-model-data").set(100);
        FireworkConfig config = node.get(FireworkConfig.class);
        assertThat(config).isNotNull();
        assertThat(config.namespaceKey()).isEqualTo("kreiscraft:firework_rocket");
    }

    @Test
    void defaultCustomModelData_whenFieldIsZero() {
        // Compact constructor maps zero (missing int) to the design default.
        FireworkConfig config = new FireworkConfig(0, "kreiscraft:firework_rocket");
        assertThat(config.customModelData()).isEqualTo(696969);
    }

    @Test
    void values_parsedFromNode() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root(ConfigurationOptions.defaults());
        node.node("custom-model-data").set(42);
        node.node("namespace-key").set("myns:myrocket");
        FireworkConfig config = node.get(FireworkConfig.class);
        assertThat(config).isNotNull();
        assertThat(config.customModelData()).isEqualTo(42);
        assertThat(config.namespaceKey()).isEqualTo("myns:myrocket");
    }

    @Test
    void explicitConstruction_preservesValues() {
        FireworkConfig config = new FireworkConfig(696969, "kreiscraft:firework_rocket");
        assertThat(config.customModelData()).isEqualTo(696969);
        assertThat(config.namespaceKey()).isEqualTo("kreiscraft:firework_rocket");
    }
}
