package de.kreiscraft.modules.worldmgmt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DimensionAccessListenerTest {

    private DimensionAccessService dimensions;
    private MessageService messages;
    private Player player;
    private DimensionAccessListener listener;

    @BeforeEach
    void setUp() {
        dimensions = mock(DimensionAccessService.class);
        messages = mock(MessageService.class);
        player = mock(Player.class);
        listener = new DimensionAccessListener(dimensions, messages);
    }

    @Test
    void disabledNether_cancelsPortalTravelAndWarnsThePlayer() {
        PlayerPortalEvent event = portalTo(World.Environment.NETHER);
        when(dimensions.isAllowed(Dimension.NETHER)).thenReturn(false);

        listener.onPortal(event);

        verify(event).setCancelled(true);
        verify(messages).send(player, "world-mgmt.dimension-blocked", "dimension", "Nether");
    }

    @Test
    void disabledEnd_cancelsPortalTravelAndWarnsThePlayer() {
        PlayerPortalEvent event = portalTo(World.Environment.THE_END);
        when(dimensions.isAllowed(Dimension.END)).thenReturn(false);

        listener.onPortal(event);

        verify(event).setCancelled(true);
        verify(messages).send(player, "world-mgmt.dimension-blocked", "dimension", "End");
    }

    @Test
    void enabledDimension_allowsPortalTravel() {
        PlayerPortalEvent event = portalTo(World.Environment.NETHER);
        when(dimensions.isAllowed(Dimension.NETHER)).thenReturn(true);

        listener.onPortal(event);

        verify(event, never()).setCancelled(true);
        verify(messages, never())
                .send(player, "world-mgmt.dimension-blocked", "dimension", "Nether");
    }

    @Test
    void overworldDestination_isNeverCancelled() {
        PlayerPortalEvent event = portalTo(World.Environment.NORMAL);

        listener.onPortal(event);

        verify(event, never()).setCancelled(true);
        verify(dimensions, never()).isAllowed(anyDimension());
    }

    @Test
    void missingDestination_isIgnored() {
        PlayerPortalEvent event = mock(PlayerPortalEvent.class);
        when(event.getTo()).thenReturn(null);

        listener.onPortal(event);

        verify(event, never()).setCancelled(true);
    }

    private PlayerPortalEvent portalTo(World.Environment environment) {
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(environment);
        Location to = mock(Location.class);
        when(to.getWorld()).thenReturn(world);
        PlayerPortalEvent event = mock(PlayerPortalEvent.class);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private static Dimension anyDimension() {
        return org.mockito.ArgumentMatchers.any(Dimension.class);
    }
}
