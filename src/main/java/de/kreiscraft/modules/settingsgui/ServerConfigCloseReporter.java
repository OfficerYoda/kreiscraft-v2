package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.message.MessageService;
import org.bukkit.entity.Player;

/**
 * Reports a closing Server Configuration session to the operator.
 *
 * <p>Only the lifecycle categories the session actually changed are reported: a hot-reloadable edit
 * gets the applicable clickable reload action, and a restart-only edit (which always includes every
 * module enable flag) gets a restart warning. A module lifecycle change is therefore never
 * described as hot-applied.
 */
public final class ServerConfigCloseReporter {

    private ServerConfigCloseReporter() {}

    /** Sends the applicable close-time prompts for {@code session}, if any. */
    public static void report(
            MessageService messages, Player player, ServerConfigSession session) {
        if (session.isEmpty()) {
            return;
        }
        if (session.hasHotReload()) {
            messages.send(player, "server-config.close.hot-reload", "count", session.hotReloadCount());
        }
        if (session.hasRestartRequired()) {
            messages.send(
                    player, "server-config.close.restart-required", "count", session.restartCount());
        }
    }
}
