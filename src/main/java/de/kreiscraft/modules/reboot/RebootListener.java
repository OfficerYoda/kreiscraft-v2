package de.kreiscraft.modules.reboot;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class RebootListener implements PriorityListener {

    private final MessageService messages;

    public RebootListener(MessageService messages) {
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        String lower = msg.toLowerCase();
        String cmd = lower.split(" ")[0];

        if ("/seed".equals(cmd) || "/minecraft:seed".equals(cmd)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "reboot.seed-blocked");
            return;
        }

        if ("/stop".equals(cmd)
                || "/restart".equals(cmd)
                || "/spigot:restart".equals(cmd)
                || "/minecraft:stop".equals(cmd)) {
            event.setMessage("/reboot");
        }
    }
}
