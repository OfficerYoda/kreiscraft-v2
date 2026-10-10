package de.kreiscraft.modules.admin;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class AdminListener implements PriorityListener {

    private final MessageService messages;

    public AdminListener(MessageService messages) {
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase().split(" ")[0];

        if ("/seed".equals(command) || "/minecraft:seed".equals(command)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "admin.seed-blocked");
        }
    }
}
