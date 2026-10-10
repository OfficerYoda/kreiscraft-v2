package de.kreiscraft.modules.updatemode;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerLoginEvent;

public final class UpdateModeListener implements PriorityListener {

    private final UpdateModeService service;
    private final MessageService messages;

    public UpdateModeListener(UpdateModeService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    // PlayerLoginEvent is deprecated by Paper (since 1.21.6) but remains the only login-stage
    // event that exposes a Player, which is required to evaluate the configured bypass permission.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (!service.isActive()) {
            return;
        }
        Player player = event.getPlayer();
        if (service.isBypassed(player)) {
            return;
        }
        event.disallow(
                PlayerLoginEvent.Result.KICK_WHITELIST,
                messages.renderFor(player, "update-mode.kick-message"));
    }
}
