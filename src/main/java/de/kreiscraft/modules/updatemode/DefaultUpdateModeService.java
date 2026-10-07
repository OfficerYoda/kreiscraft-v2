package de.kreiscraft.modules.updatemode;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.ServerConfigSchema;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Default {@link UpdateModeService}.
 *
 * <p>Per ADR-10 the active/inactive maintenance state is operator-owned Server Configuration, so
 * it lives in {@code modules.update-mode.active} inside {@code config.yml} and is read live. A
 * {@code /kreiscraft reload} therefore observes the persisted value without any extra state.
 */
public final class DefaultUpdateModeService implements UpdateModeService {

    private static final String MODULE = "update-mode";
    private static final String ACTIVE = "active";

    private final ConfigService config;
    private final PaperPlatform platform;
    private final MessageService messages;
    private final Logger logger;
    private UpdateModeConfig moduleConfig;

    public DefaultUpdateModeService(
            ConfigService config,
            UpdateModeConfig moduleConfig,
            PaperPlatform platform,
            MessageService messages,
            Logger logger) {
        this.config = config;
        this.moduleConfig = moduleConfig;
        this.platform = platform;
        this.messages = messages;
        this.logger = logger;
    }

    @Override
    public boolean isActive() {
        return config.module(MODULE).node(ACTIVE).getBoolean(false);
    }

    @Override
    public void setActive(boolean active) {
        try {
            config.applyEdit(ServerConfigSchema.updateModeActive(), active);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warning("update-mode: failed to persist state: " + e.getMessage());
            return;
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
                player.kick(messages.render("update-mode.kick-message"));
            }
        }
    }

    /** Whether the given player may stay online while maintenance mode is active. */
    @Override
    public boolean isBypassed(Player player) {
        UpdateModeConfig cfg = moduleConfig;
        return cfg.bypassNames().contains(player.getName())
                || player.isOp()
                || player.hasPermission(cfg.bypassPermission());
    }

    public UpdateModeConfig config() {
        return moduleConfig;
    }

    public void updateConfig(UpdateModeConfig config) {
        this.moduleConfig = config;
    }
}
