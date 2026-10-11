package de.kreiscraft.modules.aichat;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.function.Supplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class AiChatListener implements Listener {

    private final Supplier<AiChatConfig> config;

    public AiChatListener(Supplier<AiChatConfig> config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        AiChatConfig cfg = config.get();
        if (cfg.apiKey().isEmpty()) {
            return;
        }
        // TODO: implement AI chat handling
    }
}
