package de.kreiscraft.modules.worldmgmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;

class WorldMgmtConfigTest {

    @Test
    void emptyConfig_deserializesWithoutError() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root();
        WorldMgmtConfig config = node.get(WorldMgmtConfig.class);
        assertThat(config).isNotNull();
    }

    @Test
    void emptyConfig_roundTrip() {
        assertThatNoException().isThrownBy(() -> {
            BasicConfigurationNode node = BasicConfigurationNode.root();
            WorldMgmtConfig original = new WorldMgmtConfig();
            node.set(WorldMgmtConfig.class, original);
            WorldMgmtConfig loaded = node.get(WorldMgmtConfig.class);
            assertThat(loaded).isNotNull();
        });
    }
}
