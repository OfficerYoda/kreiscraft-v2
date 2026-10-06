package de.kreiscraft.modules.clickthrough;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

class ClickthroughConfigTest {

    private static final ConfigurationOptions OPTS =
            ConfigurationOptions.defaults().serializers(KreiscraftSerializers.builder().build());

    @Test
    void emptyNode_deserializesToClickthroughConfig() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        ClickthroughConfig cfg = node.get(ClickthroughConfig.class);
        assertThat(cfg).isNotNull();
    }

    /**
     * The target distance is fixed in code, but configs written before that change still contain the
     * old key. Deserialization must ignore it rather than fail on an existing server.
     */
    @Test
    void legacyTargetBlockMaxDistanceKey_isIgnored() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        node.node("target-block-max-distance").set(10);
        ClickthroughConfig cfg = node.get(ClickthroughConfig.class);
        assertThat(cfg).isNotNull();
    }
}
