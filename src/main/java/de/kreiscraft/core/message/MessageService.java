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

    /**
     * Resolves the {@link java.util.Locale} to use when formatting locale-sensitive values such as
     * dates for {@code recipient}. The default is English so test doubles and alternative
     * implementations only need to override it when they render locale-aware output.
     */
    default java.util.Locale localeFor(CommandSender recipient) {
        return java.util.Locale.ENGLISH;
    }

    void send(CommandSender sender, String key, Object... placeholders);

    void broadcast(String key, Object... placeholders);

    void reload() throws IOException;
}
