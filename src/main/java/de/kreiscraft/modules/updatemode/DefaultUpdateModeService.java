package de.kreiscraft.modules.updatemode;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import java.io.IOException;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

public final class DefaultUpdateModeService implements UpdateModeService {

    private final UpdateModeData data;
    private final PaperPlatform platform;
    private final MessageService messages;
    private final Logger logger;
    private UpdateModeConfig config;

    public DefaultUpdateModeService(
            UpdateModeData data,
            UpdateModeConfig config,
            PaperPlatform platform,
            MessageService messages,
            Logger logger) {
        this.data = data;
        this.config = config;
        this.platform = platform;
        this.messages = messages;
        this.logger = logger;
    }

    @Override
    public boolean isActive() {
        return data.get()[0];
    }

    @Override
    public void setActive(boolean active) {
        data.update(s -> s[0] = active);
        try {
            data.flushIfDirty();
        } catch (IOException e) {
            logger.warning("update-mode: failed to persist state: " + e.getMessage());
        }
        if (active) {
            disconnectNonBypassedPlayers();
        }
    }

    /**
     * Immediately disconnects every online player who is not allowed to bypass maintenance mode.
     * Players who may bypass stay online and receive an activation notification.
     */
    private void disconnectNonBypassedPlayers() {
        for (Player player : platform.server().getOnlinePlayers()) {
            if (isBypassed(player)) {
                messages.send(player, "update-mode.bypass-notice");
            } else {
                player.kick(messages.renderFor(player, "update-mode.kick-message"));
            }
        }
    }

    /** Whether the given player may stay online while maintenance mode is active. */
    @Override
    public boolean isBypassed(Player player) {
        UpdateModeConfig cfg = config;
        return cfg.bypassNames().contains(player.getName())
                || player.isOp()
                || player.hasPermission(cfg.bypassPermission());
    }

    public UpdateModeConfig config() {
        return config;
    }

    public void updateConfig(UpdateModeConfig config) {
        this.config = config;
    }
}
