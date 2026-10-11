package de.kreiscraft.modules.aichat;

import de.kreiscraft.shared.chat.ChatSource;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Instant;
import java.util.function.Supplier;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class AiChatListener implements Listener {

    private final Supplier<AiChatConfig> config;
    private final AiChatService service;

    public AiChatListener(Supplier<AiChatConfig> config, AiChatService service) {
        this.config = config;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message());

        Player player = event.getPlayer();
        if (player != null
                && player.hasPermission("kreiscraft.ai-chat.use")
                && msg.contains(config.get().aiTrigger())) {
            service.aiRequest(new MinecraftChatMessage(
                    Instant.now(), ChatSource.PLAYER, player.getName(), msg));
        }
    }
}
