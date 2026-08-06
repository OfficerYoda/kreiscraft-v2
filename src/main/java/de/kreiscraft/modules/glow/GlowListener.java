package de.kreiscraft.modules.glow;

import de.kreiscraft.core.event.PriorityListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

public final class GlowListener implements PriorityListener {

    private final DefaultGlowService service;

    public GlowListener(DefaultGlowService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.removePlayer(event.getPlayer().getUniqueId());
    }
}
