package de.kreiscraft.modules.worldmgmt.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SetWorldSpawnCommandTest {

    private ServerMock server;
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messages = mock(MessageService.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setWorldSpawn_updatesWorldSpawnToPlayerLocation() {
        PlayerMock player = server.addPlayer("Alice");
        Location expected = new Location(player.getWorld(), 10, 64, -5);
        player.teleport(expected);

        SetWorldSpawnCommand.setWorldSpawn(player, messages);

        Location spawn = player.getWorld().getSpawnLocation();
        assertThat(spawn.getBlockX()).isEqualTo(10);
        assertThat(spawn.getBlockZ()).isEqualTo(-5);
    }

    @Test
    void setWorldSpawn_sendsConfirmationMessage() {
        PlayerMock player = server.addPlayer("Alice");

        SetWorldSpawnCommand.setWorldSpawn(player, messages);

        verify(messages).send(player, "world-mgmt.spawn-set");
    }

    @Test
    void build_requiresSetWorldSpawnPermission() {
        LiteralCommandNode<CommandSourceStack> node = SetWorldSpawnCommand.build(messages);

        CommandSourceStack withPerm = mock(CommandSourceStack.class);
        Player permPlayer = mock(Player.class);
        when(withPerm.getSender()).thenReturn(permPlayer);
        when(permPlayer.hasPermission("kreiscraft.worldmgmt.setworldspawn")).thenReturn(true);
        assertThat(node.getRequirement().test(withPerm)).isTrue();

        CommandSourceStack withoutPerm = mock(CommandSourceStack.class);
        Player noPermPlayer = mock(Player.class);
        when(withoutPerm.getSender()).thenReturn(noPermPlayer);
        when(noPermPlayer.hasPermission("kreiscraft.worldmgmt.setworldspawn")).thenReturn(false);
        assertThat(node.getRequirement().test(withoutPerm)).isFalse();
    }

    /** The node is named "setworldspawn"; WorldMgmtModule registers "sws" as an alias. */
    @Test
    void build_returnsNodeNamedSetworldspawn() {
        LiteralCommandNode<CommandSourceStack> node = SetWorldSpawnCommand.build(messages);
        assertThat(node.getName()).isEqualTo("setworldspawn");
    }

    @Test
    void build_returnsNonNullNode() {
        assertThat(SetWorldSpawnCommand.build(messages)).isNotNull();
    }
}
