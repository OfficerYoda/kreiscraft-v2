package de.kreiscraft.modules.signcolor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

class SignColorConfigTest {

    @Test
    void get_fromEmptyNode_returnsNonNullConfig() throws SerializationException {
        var node = CommentedConfigurationNode.root();
        var config = node.get(SignColorConfig.class);
        assertThat(config).isNotNull();
    }

    @Test
    void get_fromMissingNode_returnsNonNullConfig() throws SerializationException {
        var root = CommentedConfigurationNode.root();
        var node = root.node("sign-color");
        var config = node.get(SignColorConfig.class);
        assertThat(config).isNotNull();
    }
}
