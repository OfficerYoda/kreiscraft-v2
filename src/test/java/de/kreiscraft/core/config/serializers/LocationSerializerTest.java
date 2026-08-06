package de.kreiscraft.core.config.serializers;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;

class LocationSerializerTest {

    @Test
    void deserialize_virtualNodeReturnsNull() throws Exception {
        var root = CommentedConfigurationNode.root();
        // node("some-field") is virtual because the key was never set
        Location result = LocationSerializer.INSTANCE.deserialize(Location.class, root.node("some-field"));
        assertThat(result).isNull();
    }

    @Test
    void deserialize_nullValueNodeReturnsNull() throws Exception {
        var root = CommentedConfigurationNode.root();
        root.node("field").set(null);
        Location result = LocationSerializer.INSTANCE.deserialize(Location.class, root.node("field"));
        assertThat(result).isNull();
    }
}
