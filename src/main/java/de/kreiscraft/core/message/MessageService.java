package de.kreiscraft.core.message;

import java.io.IOException;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public interface MessageService {

    Component render(String key, Object... placeholders);

    void send(CommandSender sender, String key, Object... placeholders);

    void broadcast(String key, Object... placeholders);

    void reload() throws IOException;
}
