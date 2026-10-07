package de.kreiscraft.modules.worldmgmt.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
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
    void build_requiresWorldPermission() {
        LiteralCommandNode<CommandSourceStack> node = WorldCommand.build(messages, platform);

        CommandSourceStack withPerm = mock(CommandSourceStack.class);
        Player permPlayer = mock(Player.class);
        when(withPerm.getSender()).thenReturn(permPlayer);
        when(permPlayer.hasPermission("kreiscraft.worldmgmt.world")).thenReturn(true);
        assertThat(node.getRequirement().test(withPerm)).isTrue();

        CommandSourceStack withoutPerm = mock(CommandSourceStack.class);
        Player noPermPlayer = mock(Player.class);
        when(withoutPerm.getSender()).thenReturn(noPermPlayer);
        when(noPermPlayer.hasPermission("kreiscraft.worldmgmt.world")).thenReturn(false);
        assertThat(node.getRequirement().test(withoutPerm)).isFalse();
    }

    @Test
    void build_returnsNodeNamedWorld() {
        LiteralCommandNode<CommandSourceStack> node = WorldCommand.build(messages, platform);
        assertThat(node.getName()).isEqualTo("world");
    }

    @Test
    void build_returnsNonNullNode() {
        assertThat(WorldCommand.build(messages, platform)).isNotNull();
    }
}
