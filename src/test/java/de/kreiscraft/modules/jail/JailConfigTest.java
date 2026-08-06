package de.kreiscraft.modules.jail;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

class JailConfigTest {

    private ServerMock server;
    private World world;
    private ConfigurationOptions opts;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        opts = ConfigurationOptions.defaults().serializers(KreiscraftSerializers.builder().build());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaults_blockedCommands_whenMissing() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts);
        // Provide location (required), omit blocked-commands
        node.node("location", "world").set("world");
        node.node("location", "x").set(126.0);
        node.node("location", "y").set(91.0);
        node.node("location", "z").set(92.0);
        node.node("location", "yaw").set(0.0);
        node.node("location", "pitch").set(0.0);

        JailConfig cfg = node.get(JailConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.blockedCommands()).containsExactlyInAnyOrder("msg", "tell", "w", "pay", "r");
    }

    @Test
    void roundTrip_locationAndBlockedCommands() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts);
        node.node("location", "world").set("world");
        node.node("location", "x").set(126.0);
        node.node("location", "y").set(91.0);
        node.node("location", "z").set(92.0);
        node.node("location", "yaw").set(0.0);
        node.node("location", "pitch").set(0.0);
        node.node("blocked-commands").setList(String.class, List.of("pay", "msg"));

        JailConfig cfg = node.get(JailConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.location()).isNotNull();
        assertThat(cfg.location().getX()).isEqualTo(126.0);
        assertThat(cfg.location().getY()).isEqualTo(91.0);
        assertThat(cfg.location().getZ()).isEqualTo(92.0);
        assertThat(cfg.location().getWorld()).isEqualTo(world);
        assertThat(cfg.blockedCommands()).containsExactly("pay", "msg");
    }

    @Test
    void constructor_nullBlockedCommands_usesDefaults() {
        JailConfig cfg = new JailConfig(new Location(world, 126, 91, 92), null);
        assertThat(cfg.blockedCommands()).containsExactlyInAnyOrder("msg", "tell", "w", "pay", "r");
    }

    @Test
    void constructor_emptyBlockedCommands_usesDefaults() {
        JailConfig cfg = new JailConfig(new Location(world, 126, 91, 92), List.of());
        assertThat(cfg.blockedCommands()).containsExactlyInAnyOrder("msg", "tell", "w", "pay", "r");
    }
}
