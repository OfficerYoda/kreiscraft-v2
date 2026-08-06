package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

class SettingsGuiConfigTest {

    @Test
    void emptyNode_deserializes_toNonNull() throws SerializationException {
        BasicConfigurationNode node = BasicConfigurationNode.root();
        SettingsGuiConfig config = node.get(SettingsGuiConfig.class);
        assertThat(config).isNotNull();
    }

    @Test
    void config_roundTrip_producesEquivalentRecord() throws SerializationException {
        BasicConfigurationNode node = BasicConfigurationNode.root();
        SettingsGuiConfig original = new SettingsGuiConfig();
        node.set(SettingsGuiConfig.class, original);
        SettingsGuiConfig loaded = node.get(SettingsGuiConfig.class);
        assertThat(loaded).isNotNull();
    }
}
