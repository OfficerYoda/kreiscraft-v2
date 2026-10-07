package de.kreiscraft.modules.spawn.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.spawn.SpawnService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
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
class SpawnCommandTest {

    private ServerMock server;
    private World normalWorld;

    @Mock
    private SpawnService service;

    @Mock
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        normalWorld = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_returnsNodeNamedSpawn() {
        LiteralCommandNode<CommandSourceStack> node = SpawnCommand.build(service, messages);
        assertThat(node.getName()).isEqualTo("spawn");
    }

    @Test
    void build_hasPermissionRequirement() {
        LiteralCommandNode<CommandSourceStack> node = SpawnCommand.build(service, messages);
        // A source with the permission should be accepted; one without should not.
        CommandSourceStack withPerm = mock(CommandSourceStack.class);
        Player permPlayer = mock(Player.class);
        when(withPerm.getSender()).thenReturn(permPlayer);
        when(permPlayer.hasPermission("kreiscraft.spawn.use")).thenReturn(true);
        assertThat(node.getRequirement().test(withPerm)).isTrue();

        CommandSourceStack withoutPerm = mock(CommandSourceStack.class);
        Player noPermPlayer = mock(Player.class);
        when(withoutPerm.getSender()).thenReturn(noPermPlayer);
        when(noPermPlayer.hasPermission("kreiscraft.spawn.use")).thenReturn(false);
        assertThat(node.getRequirement().test(withoutPerm)).isFalse();
    }

    @Test
    void build_nodeIsNotNull() {
        LiteralCommandNode<CommandSourceStack> node = SpawnCommand.build(service, messages);
        assertThat(node).isNotNull();
    }

    @Test
    void build_smokeTest_doesNotThrow() {
        // Verify that constructing the command tree does not throw any exception.
        assertThat(SpawnCommand.build(service, messages)).isNotNull();
    }

    // ── execute() behaviour tests ───────────────────────────────────────────

    @Test
    void execute_inNonNormalWorld_sendsWrongWorldMessage() {
        PlayerMock player = server.addPlayer("alice");
        World nether = server.addSimpleWorld("world_nether");
        player.setLocation(new Location(nether, 0, 64, 0));
        // world_nether has NORMAL environment in MockBukkit? No — we need to check.
        // MockBukkit addSimpleWorld uses Environment.NORMAL by default.
        // Use a mock player instead so we can control getWorld().getEnvironment().
        Player mockPlayer = mock(Player.class);
        World mockNether = mock(World.class);
        when(mockNether.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(mockPlayer.getWorld()).thenReturn(mockNether);

        SpawnCommand.execute(mockPlayer, service, messages);

        verify(messages).send(mockPlayer, "spawn.wrong-world");
        verify(mockPlayer, never()).teleport(any(Location.class));
    }

    @Test
    void execute_inNormalWorld_noSpawnSet_sendsNoSpawnMessage() {
        Player mockPlayer = mock(Player.class);
        World mockWorld = mock(World.class);
        when(mockWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        when(service.spawnLocation()).thenReturn(Optional.empty());

        SpawnCommand.execute(mockPlayer, service, messages);

        verify(messages).send(mockPlayer, "spawn.no-spawn-set");
        verify(mockPlayer, never()).teleport(any(Location.class));
    }

    @Test
    void execute_inNormalWorld_outOfRange_sendsOutOfRangeMessage() {
        Player mockPlayer = mock(Player.class);
        World mockWorld = mock(World.class);
        when(mockWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        when(service.spawnLocation()).thenReturn(Optional.of(new Location(normalWorld, 0, 64, 0)));
        when(service.isInSpawnRadius(mockPlayer)).thenReturn(false);

        SpawnCommand.execute(mockPlayer, service, messages);

        verify(messages).send(mockPlayer, "spawn.out-of-range");
        verify(mockPlayer, never()).teleport(any(Location.class));
    }

    @Test
    void execute_inNormalWorld_inRange_teleportsAndSendsMessage() {
        Location spawn = new Location(normalWorld, 0, 64, 0);
        PlayerMock player = server.addPlayer("bob");
        player.setLocation(new Location(normalWorld, 5, 64, 5));

        Player mockPlayer = mock(Player.class);
        World mockWorld = mock(World.class);
        when(mockWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        when(service.spawnLocation()).thenReturn(Optional.of(spawn));
        when(service.isInSpawnRadius(mockPlayer)).thenReturn(true);
        when(mockPlayer.getLocation()).thenReturn(spawn);

        SpawnCommand.execute(mockPlayer, service, messages);

        verify(mockPlayer).teleport(spawn);
        verify(messages).send(mockPlayer, "spawn.teleported");
        verify(mockPlayer).spawnParticle(eq(org.bukkit.Particle.CRIT), any(Location.class), eq(30));
    }
}
