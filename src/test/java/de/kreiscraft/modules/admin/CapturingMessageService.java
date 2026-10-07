package de.kreiscraft.modules.admin;

import de.kreiscraft.core.message.MessageService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/** Test helper: records all keys sent via {@link #send} for assertion. */
public final class CapturingMessageService implements MessageService {

    public final List<String> sentKeys = new ArrayList<>();
    public final List<Object[]> sentPlaceholders = new ArrayList<>();
    public final List<String> renderedKeys = new ArrayList<>();

    @Override
    public void send(CommandSender sender, String key, Object... placeholders) {
        sentKeys.add(key);
        sentPlaceholders.add(placeholders);
    }

    @Override
    public Component render(String key, Object... placeholders) {
        return Component.empty();
    }

    @Override
    public Component renderFor(CommandSender recipient, String key, Object... placeholders) {
        renderedKeys.add(key);
        return Component.text(key);
    }

    @Override
    public void broadcast(String key, Object... placeholders) {
        sentKeys.add(key);
    }

    @Override
    public void reload() {}
}
