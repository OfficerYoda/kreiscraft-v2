package de.kreiscraft.modules.worldmgmt;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Cancels portal travel into a dimension whose access is disabled.
 *
 * <p>Only portal travel is intercepted. Teleports issued by commands or plugins, including {@code
 * /world}, remain available so operators can still inspect a disabled dimension. Players already
 * inside a dimension are not moved when it is disabled.
 */
public final class DimensionAccessListener implements PriorityListener {

    private final DimensionAccessService dimensions;
    private final MessageService messages;

    public DimensionAccessListener(
            DimensionAccessService dimensions, MessageService messages) {
        this.dimensions = dimensions;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        World.Environment environment = to.getWorld().getEnvironment();
        Dimension dimension = Dimension.fromEnvironment(environment).orElse(null);
        if (dimension == null || dimensions.isAllowed(dimension)) {
            return;
        }
        event.setCancelled(true);
        messages.send(
                event.getPlayer(),
                "world-mgmt.dimension-blocked",
                "dimension",
                dimension.displayName());
    }
}
