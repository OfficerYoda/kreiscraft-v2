package de.kreiscraft.modules.bedrock;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.scheduler.Scheduler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Listens for {@link AsyncPlayerPreLoginEvent} and records Bedrock players.
 *
 * <p>Because {@code AsyncPlayerPreLoginEvent} fires off the main thread, the
 * {@link BedrockService#seen} call is dispatched to the main thread via
 * {@link Scheduler#runOnMain} as required by the threading rules.
 */
public final class BedrockListener implements PriorityListener {

    private final BedrockService service;
    private final Scheduler scheduler;

    public BedrockListener(BedrockService service, Scheduler scheduler) {
        this.service = service;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!service.isBedrock(event.getName())) {
            return;
        }
        String name = event.getName();
        java.util.UUID uuid = event.getUniqueId();
        scheduler.runOnMain(() -> service.seen(name, uuid));
    }
}
