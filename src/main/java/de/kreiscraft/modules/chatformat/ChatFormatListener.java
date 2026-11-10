package de.kreiscraft.modules.chatformat;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.status.Status;
import de.kreiscraft.modules.status.StatusService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import de.kreiscraft.shared.chat.ChatSource;
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

    /**
     * Token that introduces an Xaero shared-waypoint chat payload. The payload
     * continues with a {@code ':'} separator and colon-delimited fields, e.g.
     * {@code xaero-waypoint:Home:H:100:64:200:4:...}. The Xaero mod itself
     * intercepts messages beginning with {@code xaero-waypoint:}.
     */
    private static final String XAERO_WAYPOINT = "xaero-waypoint";

    private static final String XAERO_WAYPOINT_SEPARATOR = ":";

    private final Supplier<ChatFormatConfig> configSupplier;
    private final StatusService statusService;
    private final PaperPlatform platform;
    private final ChatBridge chatBridge;
    private final MessageService messages;

    public ChatFormatListener(
            Supplier<ChatFormatConfig> configSupplier,
            StatusService statusService,
            PaperPlatform platform,
            ChatBridge chatBridge,
            MessageService messages) {
        this.configSupplier = configSupplier;
        this.statusService = statusService;
        this.platform = platform;
        this.chatBridge = chatBridge;
        this.messages = messages;
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

        // Xaero waypoint payloads pass through untouched: leave the event uncancelled so the
        // vanilla message reaches the clients (and the Xaero mod), and publish nothing.
        if (isXaeroWaypoint(plainBody)) {
            return;
        }

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
        TextColor nameColor = TextColor.color(status.color() & 0x00FFFFFF);
        TextComponent nameColored = Component.text(sender.getName()).color(nameColor);

        // Resolve custom MiniMessage tags from the format string.
        TagResolver resolver = TagResolver.builder()
                .resolver(TagResolver.resolver(
                        "player-prefix", Tag.selfClosingInserting(status.prefixComponent())))
                .resolver(TagResolver.resolver(
                        "player-suffix", Tag.selfClosingInserting(status.suffixComponent())))
                .resolver(TagResolver.resolver(
                        "kc-prefix", Tag.selfClosingInserting(messages.render("prefix"))))
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
                ChatSource.PLAYER, sender.getUniqueId(), sender.getName(), nameColor.asHexString(), rendered, plainBody,
                Instant.now()));
    }

    /**
     * Returns whether {@code message} begins exactly with the Xaero waypoint token, either as the
     * bare token or followed by the protocol's {@code ':'} separator. Near-matches such as
     * {@code xaero-waypoints} or a token that merely appears later in the message are not
     * waypoints and continue through normal formatting.
     */
    private static boolean isXaeroWaypoint(String message) {
        return message.equals(XAERO_WAYPOINT)
                || message.startsWith(XAERO_WAYPOINT + XAERO_WAYPOINT_SEPARATOR);
    }

}
