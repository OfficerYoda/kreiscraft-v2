package de.kreiscraft.modules.positions;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

class PositionsConfigTest {

    private static ConfigurationOptions opts() {
        TypeSerializerCollection all = KreiscraftSerializers.builder().build();
        return ConfigurationOptions.defaults().serializers(all);
    }

    @Test
    void defaults_whenEmpty() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        PositionsConfig cfg = node.get(PositionsConfig.class);

        assertThat(cfg).isNotNull();
        // guiRows defaults to 6 (handled in compact constructor when 0 from missing YAML)
        assertThat(cfg.guiRows()).isEqualTo(6);
        // teleportOnClick defaults to true when key is absent (null → Boolean.TRUE in compact ctor)
        assertThat(cfg.teleportOnClick()).isTrue();
    }

    @Test
    void roundTrip_customValues() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        node.node("gui-rows").set(4);
        node.node("teleport-on-click").set(true);

        PositionsConfig cfg = node.get(PositionsConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.guiRows()).isEqualTo(4);
        assertThat(cfg.teleportOnClick()).isTrue();
    }

    @Test
    void guiRows_defaultsTo6_whenZeroOrNegative() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        node.node("gui-rows").set(0);

        PositionsConfig cfg = node.get(PositionsConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.guiRows()).isEqualTo(6);
    }
}
