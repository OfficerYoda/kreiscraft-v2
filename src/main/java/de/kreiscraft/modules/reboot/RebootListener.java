package de.kreiscraft.modules.reboot;

import de.kreiscraft.core.event.PriorityListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class RebootListener implements PriorityListener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        String lower = msg.toLowerCase();
        String cmd = lower.split(" ")[0];

        if ("/stop".equals(cmd)
                || "/restart".equals(cmd)
                || "/spigot:restart".equals(cmd)
                || "/minecraft:stop".equals(cmd)) {
            event.setMessage("/reboot");
        }
    }
}
