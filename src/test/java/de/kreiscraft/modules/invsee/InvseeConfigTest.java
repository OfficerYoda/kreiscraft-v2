package de.kreiscraft.modules.invsee;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

class InvseeConfigTest {

    @Test
    void emptyConfig_deserializesFromEmptyNode() throws SerializationException {
        CommentedConfigurationNode node = CommentedConfigurationNode.root();
        InvseeConfig config = node.get(InvseeConfig.class);
        assertThat(config).isNotNull();
    }

    @Test
    void emptyConfig_roundTrip() throws SerializationException {
        CommentedConfigurationNode node = CommentedConfigurationNode.root();
        InvseeConfig original = new InvseeConfig();
        node.set(InvseeConfig.class, original);
        InvseeConfig restored = node.get(InvseeConfig.class);
        assertThat(restored).isNotNull();
    }
}
