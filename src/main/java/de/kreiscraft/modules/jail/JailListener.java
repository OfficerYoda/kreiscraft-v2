package de.kreiscraft.modules.jail;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Enforces jail restrictions:
 *
 * <ul>
 *   <li>Blocks commands in {@code config.blockedCommands} for jailed players (always allows
 *       {@code /jail}, {@code /kerker}, and {@code /kreiscrafthelp}).
 *   <li>Cancels interactions for jailed players.
 *   <li>Teleports jailed players to the jail location on join.
 * </ul>
 */
public final class JailListener implements PriorityListener {

    private final JailService service;
    private final Supplier<JailConfig> config;
    private final MessageService messages;

    public JailListener(JailService service, Supplier<JailConfig> config, MessageService messages) {
        this.service = service;
        this.config = config;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!service.isJailed(event.getPlayer().getUniqueId())) {
            return;
        }

        String commandRoot = extractRoot(event.getMessage());

        // Always allow /jail, /kerker, and /kreiscrafthelp regardless of blocked-commands list
        if (commandRoot.equals("jail")
                || commandRoot.equals("kerker")
                || commandRoot.equals("kreiscrafthelp")
                || commandRoot.equals("kchelp")) {
            return;
        }

        if (config.get().blockedCommands().contains(commandRoot)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "jail.command-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (service.isJailed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        if (!service.isJailed(event.getPlayer().getUniqueId())) {
            return;
        }
        service.jailLocation().ifPresent(event.getPlayer()::teleport);
    }

    /**
     * Extracts the root command from a {@link PlayerCommandPreprocessEvent} message.
     *
     * <p>Strips the leading {@code /}, takes the first word, and removes any namespace prefix
     * (e.g. {@code minecraft:tp} → {@code tp}).
     */
    private static String extractRoot(String message) {
        // message always starts with "/"
        String cmd = message.substring(1);
        int spaceIdx = cmd.indexOf(' ');
        String root = spaceIdx >= 0 ? cmd.substring(0, spaceIdx) : cmd;
        int colonIdx = root.indexOf(':');
        if (colonIdx >= 0) {
            root = root.substring(colonIdx + 1);
        }
        return root.toLowerCase(Locale.ROOT);
    }
}
