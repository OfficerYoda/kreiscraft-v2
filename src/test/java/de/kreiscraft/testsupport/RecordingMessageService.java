package de.kreiscraft.testsupport;

import de.kreiscraft.core.message.MessageService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * Test {@link MessageService} that renders keys verbatim and records every {@code send} call.
 *
 * <p>Placeholders are appended to the rendered key so tests can assert on the operator-facing text
 * without depending on the production MiniMessage templates.
 */
public final class RecordingMessageService implements MessageService {

    /** Keys passed to {@link #send(CommandSender, String, Object...)}, in order. */
    public final List<String> sentKeys = new ArrayList<>();

    /** Full rendered text of every {@link #send} call, in order. */
    public final List<String> sent = new ArrayList<>();

    @Override
    public Component render(String key, Object... placeholders) {
        return Component.text(renderKey(key, placeholders));
    }

    @Override
    public Component renderFor(CommandSender recipient, String key, Object... placeholders) {
        return Component.text(renderKey(key, placeholders));
    }

    @Override
    public void send(CommandSender sender, String key, Object... placeholders) {
        sentKeys.add(key);
        sent.add(renderKey(key, placeholders));
    }

    @Override
    public void broadcast(String key, Object... placeholders) {
        // no-op
    }

    @Override
    public void reload() throws IOException {
        // no-op
    }

    private static String renderKey(String key, Object... placeholders) {
        StringBuilder out = new StringBuilder(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            out.append(' ').append(placeholders[i + 1]);
        }
        return out.toString();
    }
}
