package de.kreiscraft.modules.pluginhider;

import de.kreiscraft.core.event.PriorityListener;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandSendEvent;

public final class PluginHiderListener implements PriorityListener {

    private final PluginHiderService service;

    public PluginHiderListener(PluginHiderService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (event.getPlayer().hasPermission("kreiscraft.pluginhider.bypass")) {
            return;
        }
        List<String> allowed = new ArrayList<>();
        for (String cmd : event.getCommands()) {
            if ("help".equals(cmd) || service.isAllowed(cmd)) {
                allowed.add(cmd);
            }
        }
        event.getCommands().retainAll(allowed);
    }
}
