package de.kreiscraft.modules.glow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

class GlowConfigTest {

    @Test
    void emptyNode_deserializesToGlowConfig() throws SerializationException {
        BasicConfigurationNode node = BasicConfigurationNode.root();
        GlowConfig config = node.get(GlowConfig.class);
        assertThat(config).isNotNull();
    }

    @Test
    void glowConfig_isARecord() {
        GlowConfig config = new GlowConfig();
        assertThat(config).isInstanceOf(Record.class);
    }
}
