package de.kreiscraft.core.config.serializers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

class SerializersTest {

    private ServerMock server;
    private World world;
    private ConfigurationOptions opts;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        TypeSerializerCollection all = KreiscraftSerializers.builder().build();
        opts = ConfigurationOptions.defaults().serializers(all);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void durationRoundTrip() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts);
        node.set(Duration.class, Duration.ofMinutes(5));
        assertThat(node.getString()).isEqualTo("5m");
        assertThat(node.get(Duration.class)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void locationRoundTrip() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts);
        Location original = new Location(world, 1.5, 64.0, -3.5, 90f, 45f);
        node.set(Location.class, original);

        Location restored = node.get(Location.class);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void componentRoundTrip() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts);
        Component c = MiniMessage.miniMessage().deserialize("<red>hello</red>");
        node.set(Component.class, c);
        assertThat(node.getString()).isEqualTo("<red>hello");
        assertThat(node.get(Component.class)).isEqualTo(c);
    }

    @Test
    void materialRoundTrip() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts);
        node.set(Material.class, Material.DIAMOND_SWORD);
        assertThat(node.getString()).isEqualTo("DIAMOND_SWORD");
        assertThat(node.get(Material.class)).isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    void materialIsCaseInsensitive() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts).set("diamond_sword");
        assertThat(node.get(Material.class)).isEqualTo(Material.DIAMOND_SWORD);
    }
}
