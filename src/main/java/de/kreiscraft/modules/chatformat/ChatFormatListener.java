package de.kreiscraft.modules.chatformat;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.modules.status.Status;
import de.kreiscraft.modules.status.StatusService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Instant;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public final class ChatFormatListener implements PriorityListener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Supplier<ChatFormatConfig> configSupplier;
    private final StatusService statusService;
    private final PaperPlatform platform;
    private final ChatBridge chatBridge;

    public ChatFormatListener(
            Supplier<ChatFormatConfig> configSupplier,
            StatusService statusService,
            PaperPlatform platform,
            ChatBridge chatBridge) {
        this.configSupplier = configSupplier;
        this.statusService = statusService;
        this.platform = platform;
        this.chatBridge = chatBridge;
    }

    /**
     * Priority is LOWEST so chat-format runs before other plugins that may further
     * process the formatted message. Note: the EventBus does not support runtime-
     * configurable priorities; LOWEST matches the config default and is correct for
     * a chat formatter that replaces the vanilla event entirely.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        ChatFormatConfig config = configSupplier.get();
        Player sender = event.getPlayer();

        // Security: always extract raw plain text to prevent player-injected MiniMessage.
        String plainBody = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Build the body component — only allow MiniMessage tags if explicitly opted in.
        Component bodyComponent;
        if (config.allowMiniMessageInBody()) {
            bodyComponent = MINI_MESSAGE.deserialize(plainBody);
        } else {
            bodyComponent = Component.text(plainBody);
        }

        // Look up the sender's status for prefix, suffix and name colour.
        Status status = statusService.get(sender.getUniqueId());

        // Player display name coloured with their status colour.
        TextComponent nameColored =
                Component.text(sender.getName()).color(TextColor.color(status.color() & 0x00FFFFFF));

        // Resolve custom MiniMessage tags from the format string.
        TagResolver resolver = TagResolver.builder()
                .resolver(TagResolver.resolver(
                        "team-prefix",
                        Tag.selfClosingInserting(MINI_MESSAGE.deserialize(status.prefix()))))
                .resolver(TagResolver.resolver(
                        "team-suffix",
                        Tag.selfClosingInserting(MINI_MESSAGE.deserialize(status.suffix()))))
                .resolver(TagResolver.resolver(
                        "name-colored", Tag.selfClosingInserting(nameColored)))
                .resolver(TagResolver.resolver("body", Tag.selfClosingInserting(bodyComponent)))
                .build();

        Component rendered = MINI_MESSAGE.deserialize(config.format(), resolver);

        // Cancel the vanilla chat event and take over delivery ourselves.
        event.setCancelled(true);

        // Broadcast to all online players. Safe on the async thread (Adventure/Paper queues packets).
        platform.server().sendMessage(rendered);

        // Publish to ChatBridge for the API module (also safe on any thread).
        chatBridge.publish(new ChatBridgeEvent(
                sender.getUniqueId(), sender.getName(), rendered, plainBody, Instant.now()));
    }
}
