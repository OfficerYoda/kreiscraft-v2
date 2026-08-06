package de.kreiscraft.core.config.serializers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

class VectorSerializerTest {

    @Test
    void deserialize_readsXYZFromMap() throws Exception {
        var node = CommentedConfigurationNode.root();
        node.node("x").set(1.5);
        node.node("y").set(2.5);
        node.node("z").set(3.5);
        Vector v = VectorSerializer.INSTANCE.deserialize(Vector.class, node);
        assertThat(v.getX()).isEqualTo(1.5);
        assertThat(v.getY()).isEqualTo(2.5);
        assertThat(v.getZ()).isEqualTo(3.5);
    }

    @Test
    void deserialize_missingFieldsDefaultToZero() throws Exception {
        var node = CommentedConfigurationNode.root();
        node.node("y").set(5.0);
        Vector v = VectorSerializer.INSTANCE.deserialize(Vector.class, node);
        assertThat(v.getX()).isEqualTo(0.0);
        assertThat(v.getY()).isEqualTo(5.0);
        assertThat(v.getZ()).isEqualTo(0.0);
    }

    @Test
    void deserialize_nullNodeReturnsNull() throws Exception {
        var node = CommentedConfigurationNode.root(); // virtual, no value
        assertThat(VectorSerializer.INSTANCE.deserialize(Vector.class, node)).isNull();
    }

    @Test
    void serialize_writesXYZAsMap() throws Exception {
        var node = CommentedConfigurationNode.root();
        VectorSerializer.INSTANCE.serialize(Vector.class, new Vector(1.5, 2.5, 3.5), node);
        assertThat(node.node("x").getDouble()).isEqualTo(1.5);
        assertThat(node.node("y").getDouble()).isEqualTo(2.5);
        assertThat(node.node("z").getDouble()).isEqualTo(3.5);
    }

    @Test
    void serialize_nullValueClearsNode() throws Exception {
        var node = CommentedConfigurationNode.root();
        node.node("x").set(1.0);
        VectorSerializer.INSTANCE.serialize(Vector.class, null, node);
        assertThat(node.isNull() || node.empty()).isTrue();
    }

    @Test
    void deserialize_wrongType_throws() throws Exception {
        var node = CommentedConfigurationNode.root();
        node.set("not a map");
        assertThatThrownBy(() -> VectorSerializer.INSTANCE.deserialize(Vector.class, node))
                .isInstanceOf(SerializationException.class);
    }
}
