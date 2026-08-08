package de.kreiscraft.modules.updatemode;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class UpdateModeListener implements PriorityListener {

    private final DefaultUpdateModeService service;
    private final PaperPlatform platform;
    private final MessageService messages;

    public UpdateModeListener(
            DefaultUpdateModeService service, PaperPlatform platform, MessageService messages) {
        this.service = service;
        this.platform = platform;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!service.isActive()) {
            return;
        }
        UpdateModeConfig cfg = service.config();
        String name = event.getName();
        UUID uuid = event.getUniqueId();
        if (cfg.bypassNames().contains(name)) {
            return;
        }
        if (platform.server().getOfflinePlayer(uuid).isOp()) {
            return;
        }
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                messages.render("update-mode.kick-message"));
    }
}
