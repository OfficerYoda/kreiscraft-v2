package de.kreiscraft.core.message;

import java.io.IOException;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public interface MessageService {

    Component render(String key, Object... placeholders);

    /**
     * Renders a message in the recipient's persisted language, falling back to the server language
     * when the recipient has no stored preference or the stored value is unusable.
     *
     * <p>The default implementation ignores the recipient so test doubles and alternative
     * implementations only need to override it when they care about per-recipient output.
     */
    default Component renderFor(CommandSender recipient, String key, Object... placeholders) {
        return render(key, placeholders);
    }

    void send(CommandSender sender, String key, Object... placeholders);

    void broadcast(String key, Object... placeholders);

    void reload() throws IOException;
}
