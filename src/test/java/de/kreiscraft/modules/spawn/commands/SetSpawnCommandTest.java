package de.kreiscraft.modules.spawn.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.spawn.SpawnService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetSpawnCommandTest {

    private ServerMock server;

    @Mock
    private SpawnService service;

    @Mock
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_returnsNodeNamedSetspawn() {
        LiteralCommandNode<CommandSourceStack> node = SetSpawnCommand.build(service, messages);
        assertThat(node.getName()).isEqualTo("setspawn");
    }

    @Test
    void build_requiresSetPermission() {
        LiteralCommandNode<CommandSourceStack> node = SetSpawnCommand.build(service, messages);

        CommandSourceStack withPerm = mock(CommandSourceStack.class);
        Player permPlayer = mock(Player.class);
        when(withPerm.getSender()).thenReturn(permPlayer);
        when(permPlayer.hasPermission("kreiscraft.spawn.set")).thenReturn(true);
        assertThat(node.getRequirement().test(withPerm)).isTrue();

        CommandSourceStack withoutPerm = mock(CommandSourceStack.class);
        Player noPermPlayer = mock(Player.class);
        when(withoutPerm.getSender()).thenReturn(noPermPlayer);
        when(noPermPlayer.hasPermission("kreiscraft.spawn.set")).thenReturn(false);
        assertThat(node.getRequirement().test(withoutPerm)).isFalse();
    }

    @Test
    void build_nodeIsNotNull() {
        LiteralCommandNode<CommandSourceStack> node = SetSpawnCommand.build(service, messages);
        assertThat(node).isNotNull();
    }

    @Test
    void build_smokeTest_doesNotThrow() {
        // Verify that constructing the command tree does not throw any exception.
        PlayerMock player = server.addPlayer("alice");
        assertThat(SetSpawnCommand.build(service, messages)).isNotNull();
    }

    @Test
    void build_nodeHasRadiusArgument() {
        LiteralCommandNode<CommandSourceStack> node = SetSpawnCommand.build(service, messages);
        // The radius argument is the only child of the setspawn literal.
        assertThat(node.getChildren()).isNotEmpty();
        assertThat(node.getChildren().iterator().next().getName()).isEqualTo("radius");
    }
}
