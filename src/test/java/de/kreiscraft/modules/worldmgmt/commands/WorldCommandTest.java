package de.kreiscraft.modules.worldmgmt.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class WorldCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private MessageService messages;
    private PaperPlatform platform;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = mock(MessageService.class);
        platform = new PaperPlatform(plugin, server);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void teleportToWorld_unknownWorld_sendsErrorMessage() {
        PlayerMock player = server.addPlayer("Alice");

        WorldCommand.teleportToWorld(player, "nonexistent", platform, messages);

        verify(messages).send(player, "world-mgmt.unknown-world", "world", "nonexistent");
    }

    @Test
    void teleportToWorld_unknownWorld_doesNotTeleport() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        WorldCommand.teleportToWorld(player, "nonexistent", platform, messages);

        // Player stays in default world
        assertThat(player.getWorld().getName()).isEqualTo("world");
    }

    @Test
    void teleportToWorld_validWorld_teleportsPlayer() {
        PlayerMock player = server.addPlayer("Alice");
        WorldMock target = server.addSimpleWorld("the_nether");

        WorldCommand.teleportToWorld(player, "the_nether", platform, messages);

        assertThat(player.getWorld().getName()).isEqualTo("the_nether");
    }

    @Test
    void teleportToWorld_validWorld_sendsSuccessMessage() {
        PlayerMock player = server.addPlayer("Alice");
        server.addSimpleWorld("the_nether");

        WorldCommand.teleportToWorld(player, "the_nether", platform, messages);

        verify(messages).send(player, "world-mgmt.teleported", "world", "the_nether");
    }

    @Test
    void teleportToWorld_validWorld_noErrorMessage() {
        PlayerMock player = server.addPlayer("Alice");
        server.addSimpleWorld("the_nether");

        WorldCommand.teleportToWorld(player, "the_nether", platform, messages);

        verify(messages, never())
                .send(player, "world-mgmt.unknown-world", "world", "the_nether");
    }

    @Test
    void build_returnsNonNullNode() {
        assertThat(WorldCommand.build(messages, platform)).isNotNull();
    }
}
