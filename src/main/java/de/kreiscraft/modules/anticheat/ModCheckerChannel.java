package de.kreiscraft.modules.anticheat;

import de.kreiscraft.core.message.MessageService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ModCheckerChannel implements PluginMessageListener {

    private final DefaultHackLogService service;
    private final MessageService messages;

    public ModCheckerChannel(DefaultHackLogService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        String payload = new String(bytes, StandardCharsets.UTF_8);
        int colon = payload.indexOf(':');
        if (colon < 0) {
            return; // malformed: no colon
        }
        String type = payload.substring(0, colon);
        String value = payload.substring(colon + 1);
        if (value.isEmpty()) {
            return; // malformed: empty value

        }
        switch (type) {
            case "cheats" -> {
                service.record(new HackEntry(
                        player.getUniqueId(), player.getName(), HackEntry.Kind.CHEAT, value, Instant.now()));
                player.kick(messages.render("anti-cheat.cheat-kick", "detail", value));
            }
            case "xray" -> {
                service.record(new HackEntry(
                        player.getUniqueId(), player.getName(), HackEntry.Kind.XRAY, value, Instant.now()));
                player.kick(messages.render("anti-cheat.xray-kick", "detail", value));
            }
            case "freecam" -> {
                if ("false".equals(value)) {
                    service.markHandshakePassed(player.getUniqueId());
                } else if ("true".equals(value)) {
                    player.kick(messages.render("anti-cheat.freecam-kick"));
                }
            }
            default -> {
                // unknown type — ignore silently
            }
        }
    }
}
