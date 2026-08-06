package de.kreiscraft.modules.updatemode;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.SerializationException;

class UpdateModeConfigTest {

    private static CommentedConfigurationNode emptyNode() {
        return CommentedConfigurationNode.root(
                ConfigurationOptions.defaults()
                        .serializers(KreiscraftSerializers.builder().build()));
    }

    @Test
    void defaults_whenConfigEmpty() throws SerializationException {
        UpdateModeConfig cfg = emptyNode().get(UpdateModeConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.bypassPermission()).isEqualTo("kreiscraft.updatemode.bypass");
        assertThat(cfg.bypassNames()).isEmpty();
    }

    @Test
    void parsesExplicitValues() throws SerializationException {
        CommentedConfigurationNode node = emptyNode();
        node.node("bypass-permission").set("kreiscraft.custom.bypass");
        node.node("bypass-names").setList(String.class, List.of("admin", "tester"));

        UpdateModeConfig cfg = node.get(UpdateModeConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.bypassPermission()).isEqualTo("kreiscraft.custom.bypass");
        assertThat(cfg.bypassNames()).containsExactly("admin", "tester");
    }

    @Test
    void roundTrip_preservesValues() throws SerializationException {
        CommentedConfigurationNode node = emptyNode();
        node.node("bypass-permission").set("kreiscraft.updatemode.bypass");
        node.node("bypass-names").setList(String.class, List.of("alice"));

        UpdateModeConfig first = node.get(UpdateModeConfig.class);
        assertThat(first).isNotNull();
        assertThat(first.bypassNames()).containsExactly("alice");
    }
}
