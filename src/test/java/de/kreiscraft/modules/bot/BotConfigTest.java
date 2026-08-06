package de.kreiscraft.modules.bot;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.SerializationException;

class BotConfigTest {

    @Test
    void deserialize_fromEmptyNode_returnsInstance() throws SerializationException {
        ConfigurationOptions opts = ConfigurationOptions.defaults()
                .serializers(KreiscraftSerializers.builder().build());
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts);

        BotConfig config = node.get(BotConfig.class);

        assertThat(config).isNotNull();
    }

    @Test
    void deserialize_fromEmptySection_returnsInstance() throws SerializationException {
        ConfigurationOptions opts = ConfigurationOptions.defaults()
                .serializers(KreiscraftSerializers.builder().build());
        CommentedConfigurationNode root = CommentedConfigurationNode.root(opts);
        root.node("bot"); // empty section

        BotConfig config = root.node("bot").get(BotConfig.class);

        assertThat(config).isNotNull();
    }
}
