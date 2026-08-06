package de.kreiscraft.modules.updatemode;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.platform.PaperPlatform;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class UpdateModeListener implements PriorityListener {

    private final DefaultUpdateModeService service;
    private final PaperPlatform platform;

    public UpdateModeListener(DefaultUpdateModeService service, PaperPlatform platform) {
        this.service = service;
        this.platform = platform;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!service.isActive())
            return;
        UpdateModeConfig cfg = service.config();
        String name = event.getName();
        UUID uuid = event.getUniqueId();
        if (cfg.bypassNames().contains(name))
            return;
        if (platform.server().getOfflinePlayer(uuid).isOp())
            return;
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, cfg.kickMessage());
    }
}
