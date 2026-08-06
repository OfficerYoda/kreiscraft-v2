package de.kreiscraft.modules.worldmgmt.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.worldmgmt.commands.PortalCommand.PortalDirection;
import de.kreiscraft.platform.PaperPlatform;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class PortalCommandTest {

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

    // --- executePortal integration tests ---

    @Test
    void executePortal_nether_noTargetWorld_sendsErrorMessage() {
        // MockBukkit only creates a NORMAL world by default — no nether world
        PlayerMock player = server.addPlayer("Alice");

        PortalCommand.executePortal(player, PortalDirection.NETHER, 800, 800, platform, messages);

        verify(messages).send(player, "world-mgmt.portal-no-target");
    }

    @Test
    void executePortal_nether_withNetherWorld_teleportsPlayer() {
        PlayerMock player = server.addPlayer("Alice");
        WorldMock netherWorld = server.addSimpleWorld("world_nether");
        netherWorld.setEnvironment(World.Environment.NETHER);

        PortalCommand.executePortal(player, PortalDirection.NETHER, 800, 800, platform, messages);

        assertThat(player.getWorld().getEnvironment()).isEqualTo(World.Environment.NETHER);
    }

    @Test
    void executePortal_nether_convertsCoordinates() {
        PlayerMock player = server.addPlayer("Alice");
        WorldMock netherWorld = server.addSimpleWorld("world_nether");
        netherWorld.setEnvironment(World.Environment.NETHER);

        PortalCommand.executePortal(player, PortalDirection.NETHER, 800, 160, platform, messages);

        assertThat(player.getLocation().getBlockX()).isEqualTo(100);
        assertThat(player.getLocation().getBlockZ()).isEqualTo(20);
    }

    @Test
    void executePortal_nether_sendsCoordinateMessage() {
        PlayerMock player = server.addPlayer("Alice");
        WorldMock netherWorld = server.addSimpleWorld("world_nether");
        netherWorld.setEnvironment(World.Environment.NETHER);

        PortalCommand.executePortal(player, PortalDirection.NETHER, 800, 160, platform, messages);

        int expectedY = netherWorld.getSpawnLocation().getBlockY();
        verify(messages)
                .send(
                        player,
                        "world-mgmt.portal-converted",
                        "x", 100,
                        "y", expectedY,
                        "z", 20);
    }

    @Test
    void executePortal_overworld_convertsCoordinates() {
        PlayerMock player = server.addPlayer("Alice");
        WorldMock netherWorld = server.addSimpleWorld("world_nether");
        netherWorld.setEnvironment(World.Environment.NETHER);
        player.teleport(netherWorld.getSpawnLocation());

        PortalCommand.executePortal(player, PortalDirection.OVERWORLD, 100, 20, platform, messages);

        assertThat(player.getWorld().getEnvironment()).isEqualTo(World.Environment.NORMAL);
        assertThat(player.getLocation().getBlockX()).isEqualTo(800);
        assertThat(player.getLocation().getBlockZ()).isEqualTo(160);
    }

    @Test
    void build_returnsNonNullNode() {
        assertThat(PortalCommand.build(messages, platform)).isNotNull();
    }
}
