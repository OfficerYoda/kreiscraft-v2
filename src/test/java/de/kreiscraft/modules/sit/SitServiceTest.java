package de.kreiscraft.modules.sit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
        service = new SitService(messages);
        server.getPluginManager().registerEvents(service, MockBukkit.createMockPlugin());
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
