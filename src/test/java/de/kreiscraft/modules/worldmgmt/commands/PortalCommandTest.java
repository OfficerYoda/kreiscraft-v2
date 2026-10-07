package de.kreiscraft.modules.worldmgmt.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.worldmgmt.commands.PortalCommand.PortalDirection;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class PortalCommandTest {

    private ServerMock server;
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        messages = mock(MessageService.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- Coordinate conversion unit tests ---

    @Test
    void convert_nether_dividesBy8() {
        assertThat(PortalCommand.convert(800, PortalDirection.NETHER)).isEqualTo(100);
    }

    @Test
    void convert_nether_zero_remainsZero() {
        assertThat(PortalCommand.convert(0, PortalDirection.NETHER)).isEqualTo(0);
    }

    @Test
    void convert_nether_negative_dividesBy8() {
        assertThat(PortalCommand.convert(-80, PortalDirection.NETHER)).isEqualTo(-10);
    }

    @Test
    void convert_overworld_multipliesBy8() {
        assertThat(PortalCommand.convert(100, PortalDirection.OVERWORLD)).isEqualTo(800);
    }

    @Test
    void convert_overworld_zero_remainsZero() {
        assertThat(PortalCommand.convert(0, PortalDirection.OVERWORLD)).isEqualTo(0);
    }

    @Test
    void convert_overworld_negative_multipliesBy8() {
        assertThat(PortalCommand.convert(-10, PortalDirection.OVERWORLD)).isEqualTo(-80);
    }

    @Test
    void convert_roundTrip_nether_thenOverworld() {
        int original = 800;
        int nether = PortalCommand.convert(original, PortalDirection.NETHER);
        int back = PortalCommand.convert(nether, PortalDirection.OVERWORLD);
        assertThat(back).isEqualTo(original);
    }

    // --- Explicit conversion (both directions, no env check) ---

    @Test
    void executePortal_nether_sendsConvertedCoords() {
        PlayerMock player = server.addPlayer("Alice");

        PortalCommand.executePortal(player, PortalDirection.NETHER, 800, 160, messages);

        verify(messages).send(player, "world-mgmt.portal-converted", "x", 100, "z", 20);
    }

    @Test
    void executePortal_overworld_sendsConvertedCoords() {
        PlayerMock player = server.addPlayer("Alice");

        PortalCommand.executePortal(player, PortalDirection.OVERWORLD, 100, 20, messages);

        verify(messages).send(player, "world-mgmt.portal-converted", "x", 800, "z", 160);
    }

    @Test
    void executePortal_nether_negative_sendsConvertedCoords() {
        PlayerMock player = server.addPlayer("Alice");

        PortalCommand.executePortal(player, PortalDirection.NETHER, -80, -160, messages);

        verify(messages).send(player, "world-mgmt.portal-converted", "x", -10, "z", -20);
    }

    @Test
    void executePortal_overworld_negative_sendsConvertedCoords() {
        PlayerMock player = server.addPlayer("Alice");

        PortalCommand.executePortal(player, PortalDirection.OVERWORLD, -10, -20, messages);

        verify(messages).send(player, "world-mgmt.portal-converted", "x", -80, "z", -160);
    }

    @Test
    void executePortal_doesNotTeleportPlayer() {
        PlayerMock player = server.addPlayer("Alice");
        Location initial = player.getLocation().clone();

        PortalCommand.executePortal(player, PortalDirection.NETHER, 800, 800, messages);

        assertThat(player.getLocation().getBlockX()).isEqualTo(initial.getBlockX());
        assertThat(player.getWorld().getName()).isEqualTo(initial.getWorld().getName());
    }

    // --- Implicit conversion (environment enforced) ---

    @Test
    void executeImplicitPortal_nether_whenInOverworld_sendsConvertedCoords() {
        PlayerMock player = server.addPlayer("Alice");
        // Default world is NORMAL (overworld)
        player.teleport(new Location(player.getWorld(), 800, 64, 160));

        PortalCommand.executeImplicitPortal(player, PortalDirection.NETHER, messages);

        verify(messages).send(player, "world-mgmt.portal-converted", "x", 100, "z", 20);
    }

    @Test
    void executeImplicitPortal_nether_whenInNether_sendsEnvError() {
        PlayerMock player = server.addPlayer("Alice");
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        player.teleport(nether.getSpawnLocation());

        PortalCommand.executeImplicitPortal(player, PortalDirection.NETHER, messages);

        verify(messages).send(player, "world-mgmt.portal-not-overworld");
    }

    @Test
    void executeImplicitPortal_nether_whenInNether_doesNotSendConvertedCoords() {
        PlayerMock player = server.addPlayer("Alice");
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        player.teleport(nether.getSpawnLocation());

        PortalCommand.executeImplicitPortal(player, PortalDirection.NETHER, messages);

        verify(messages, never()).send(player, "world-mgmt.portal-converted",
                "x", player.getLocation().getBlockX() / 8,
                "z", player.getLocation().getBlockZ() / 8);
    }

    @Test
    void executeImplicitPortal_overworld_whenInNether_sendsConvertedCoords() {
        PlayerMock player = server.addPlayer("Alice");
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        player.teleport(new Location(nether, 100, 64, 20));

        PortalCommand.executeImplicitPortal(player, PortalDirection.OVERWORLD, messages);

        verify(messages).send(player, "world-mgmt.portal-converted", "x", 800, "z", 160);
    }

    @Test
    void executeImplicitPortal_overworld_whenInOverworld_sendsEnvError() {
        PlayerMock player = server.addPlayer("Alice");
        // Default world is NORMAL (overworld)

        PortalCommand.executeImplicitPortal(player, PortalDirection.OVERWORLD, messages);

        verify(messages).send(player, "world-mgmt.portal-not-nether");
    }

    @Test
    void executeImplicitPortal_doesNotTeleportPlayer() {
        PlayerMock player = server.addPlayer("Alice");
        String initialWorld = player.getWorld().getName();

        PortalCommand.executeImplicitPortal(player, PortalDirection.NETHER, messages);

        assertThat(player.getWorld().getName()).isEqualTo(initialWorld);
    }

    // --- Permission ---

    @Test
    void build_requiresPortalPermission() {
        LiteralCommandNode<CommandSourceStack> node = PortalCommand.build(messages);

        CommandSourceStack withPerm = mock(CommandSourceStack.class);
        Player permPlayer = mock(Player.class);
        when(withPerm.getSender()).thenReturn(permPlayer);
        when(permPlayer.hasPermission("kreiscraft.worldmgmt.portal")).thenReturn(true);
        assertThat(node.getRequirement().test(withPerm)).isTrue();

        CommandSourceStack withoutPerm = mock(CommandSourceStack.class);
        Player noPermPlayer = mock(Player.class);
        when(withoutPerm.getSender()).thenReturn(noPermPlayer);
        when(noPermPlayer.hasPermission("kreiscraft.worldmgmt.portal")).thenReturn(false);
        assertThat(node.getRequirement().test(withoutPerm)).isFalse();
    }

    @Test
    void build_returnsNonNullNode() {
        assertThat(PortalCommand.build(messages)).isNotNull();
    }
}
