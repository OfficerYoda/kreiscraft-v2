package de.kreiscraft.core.module;

import de.kreiscraft.core.message.MessageService;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Notifies operators who join after startup about modules that failed to enable. */
public final class ModuleFailureNotificationListener implements Listener {

    private final List<String> failedModuleIds;
    private final MessageService messages;

    public ModuleFailureNotificationListener(List<String> failedModuleIds, MessageService messages) {
        this.failedModuleIds = List.copyOf(failedModuleIds);
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (failedModuleIds.isEmpty() || !event.getPlayer().isOp()) {
            return;
        }
        messages.send(event.getPlayer(), "core.startup.failed-modules",
                "modules", String.join(", ", failedModuleIds));
    }
}
