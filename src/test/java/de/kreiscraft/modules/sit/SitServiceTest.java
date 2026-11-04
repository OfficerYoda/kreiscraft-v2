package de.kreiscraft.modules.sit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.BukkitScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SitServiceTest {

    private ServerMock server;
    private PlayerMock player;
    private MessageService messages;
    private SitService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        messages = mock(MessageService.class);
        var plugin = MockBukkit.createMockPlugin();
        service = new SitService(messages, new BukkitScheduler(plugin));
        server.getPluginManager().registerEvents(service, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void toggle_sitsThenStandsAndRemovesSeat() {
        service.toggle(player);

        ArmorStand seat = (ArmorStand) player.getVehicle();
        assertThat(seat).isNotNull();
        assertThat(seat.isVisible()).isFalse();
        assertThat(seat.isMarker()).isTrue();
        assertThat(seat.hasGravity()).isFalse();
        assertThat(seat.isInvulnerable()).isTrue();
        assertThat(seat.isPersistent()).isFalse();
        verify(messages).send(player, "sit.sat");

        service.toggle(player);

        assertThat(seat.isValid()).isFalse();
        assertThat(player.getVehicle()).isNull();
        verify(messages).send(player, "sit.stood");
    }

    @Test
    void dismount_removesSeatAndAllowsSittingAgain() {
        service.toggle(player);
        ArmorStand seat = (ArmorStand) player.getVehicle();

        assertThat(player.leaveVehicle()).isTrue();

        assertThat(seat.isValid()).isFalse();
        service.toggle(player);
        assertThat(player.getVehicle()).isNotNull().isNotSameAs(seat);
    }

    @Test
    void sneakDismount_returnsPlayerAboveTheBlockAfterVanillaPositionsThem() {
        Location standing = new Location(player.getWorld(), 0.5, 65.0, 0.5);
        player.getWorld().getBlockAt(0, 64, 0).setType(Material.STONE);
        player.setLocation(standing);
        service.toggle(player);

        assertThat(player.leaveVehicle()).isTrue();
        // MockBukkit has no passenger physics. Replay the reported vanilla exit position,
        // applied after EntityDismountEvent, with the player's feet inside the supporting block.
        player.setLocation(standing.clone().subtract(0, 0.35, 0));
        server.getScheduler().performOneTick();

        assertThat(player.getLocation().getY()).isGreaterThanOrEqualTo(standing.getY());
        assertThat(player.getLocation().getBlock().getType()).isEqualTo(Material.AIR);
    }

    @Test
    void commandStand_restoresOriginalFeetPositionAndPreservesCurrentView() {
        Location standing = new Location(player.getWorld(), 0.5, 65.0, 0.5);
        player.setLocation(standing);
        service.toggle(player);
        Location sitting = standing.clone().subtract(0, 0.35, 0);
        sitting.setYaw(90);
        sitting.setPitch(20);
        player.setLocation(sitting);

        service.toggle(player);

        assertThat(player.getLocation()).isEqualTo(new Location(player.getWorld(), 0.5, 65.0, 0.5, 90, 20));
    }

    @Test
    void teleportAfterDismount_isNotUndoneByPendingRestore() {
        service.toggle(player);
        player.leaveVehicle();
        Location destination = new Location(player.getWorld(), 10.5, 80.0, 10.5);

        player.teleport(destination);
        server.getScheduler().performOneTick();

        assertThat(player.getLocation()).isEqualTo(destination);
    }

    @Test
    void teleportWhileSitting_isNotUndoneByCleanup() {
        service.toggle(player);
        Location destination = new Location(player.getWorld(), 10.5, 80.0, 10.5);

        player.teleport(destination);
        server.getScheduler().performOneTick();

        assertThat(player.getLocation()).isEqualTo(destination);
        assertThat(player.getVehicle()).isNull();
    }

    @Test
    void cancelledDismount_keepsSeatAndDoesNotRestorePosition() {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onDismount(EntityDismountEvent event) {
                event.setCancelled(true);
            }
        }, MockBukkit.createMockPlugin());
        service.toggle(player);
        ArmorStand seat = (ArmorStand) player.getVehicle();
        Location sitting = player.getLocation().subtract(0, 0.35, 0);
        player.setLocation(sitting);

        // MockBukkit's leaveVehicle does not honour cancellation; exercise event dispatch directly.
        EntityDismountEvent event = new EntityDismountEvent(player, seat);
        server.getPluginManager().callEvent(event);
        assertThat(event.isCancelled()).isTrue();
        server.getScheduler().performOneTick();

        assertThat(player.getVehicle()).isSameAs(seat);
        assertThat(player.getLocation()).isEqualTo(sitting);
    }

    @Test
    void newSeatBeforePendingRestore_isNotDismountedByOldTask() {
        service.toggle(player);
        player.leaveVehicle();
        service.toggle(player);
        ArmorStand newSeat = (ArmorStand) player.getVehicle();

        server.getScheduler().performOneTick();

        assertThat(player.getVehicle()).isSameAs(newSeat);
        assertThat(newSeat.isValid()).isTrue();
    }

    @Test
    void disableWhileRestorePending_restoresImmediatelyAndLeavesNoDeferredTeleport() {
        Location standing = new Location(player.getWorld(), 0.5, 65.0, 0.5);
        player.setLocation(standing);
        service.toggle(player);
        player.leaveVehicle();
        player.setLocation(standing.clone().subtract(0, 0.35, 0));

        service.standAll();

        assertThat(player.getLocation()).isEqualTo(standing);
        Location moved = standing.clone().add(5, 0, 0);
        player.setLocation(moved);
        server.getScheduler().performOneTick();
        assertThat(player.getLocation()).isEqualTo(moved);
    }

    @Test
    void existingVehicle_isLeftAlone() {
        ArmorStand vehicle = player.getWorld().spawn(player.getLocation(), ArmorStand.class);
        vehicle.addPassenger(player);

        service.toggle(player);

        assertThat(player.getVehicle()).isSameAs(vehicle);
        verify(messages).send(player, "sit.unavailable");
    }

    @Test
    void quit_removesSeat() {
        service.toggle(player);
        ArmorStand seat = (ArmorStand) player.getVehicle();

        service.onQuit(new PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null,
                PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(seat.isValid()).isFalse();
    }

    @Test
    void teleport_removesSeat() {
        service.toggle(player);
        ArmorStand seat = (ArmorStand) player.getVehicle();

        service.onTeleport(new PlayerTeleportEvent(player, player.getLocation(), player.getLocation()));

        assertThat(seat.isValid()).isFalse();
    }

    @Test
    void death_removesSeat() {
        service.toggle(player);
        ArmorStand seat = (ArmorStand) player.getVehicle();
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);

        service.onDeath(event);

        assertThat(seat.isValid()).isFalse();
    }

    @Test
    void cancelledMount_doesNotLeaveSeatBehind() {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onMount(EntityMountEvent event) {
                event.setCancelled(true);
            }
        }, MockBukkit.createMockPlugin());

        service.toggle(player);

        assertThat(player.getVehicle()).isNull();
        assertThat(player.getWorld().getEntitiesByClass(ArmorStand.class)).isEmpty();
        verify(messages).send(player, "sit.unavailable");
    }

    @Test
    void disable_removesAllSeats() {
        PlayerMock other = server.addPlayer();
        service.toggle(player);
        service.toggle(other);
        ArmorStand first = (ArmorStand) player.getVehicle();
        ArmorStand second = (ArmorStand) other.getVehicle();

        service.standAll();
        service.standAll();

        assertThat(first.isValid()).isFalse();
        assertThat(second.isValid()).isFalse();
        assertThat(player.getVehicle()).isNull();
        assertThat(other.getVehicle()).isNull();
    }
}
