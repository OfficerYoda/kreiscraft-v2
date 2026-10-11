package de.kreiscraft.modules.aichat;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import de.kreiscraft.shared.chat.ChatSource;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class DefaultAiChatService implements AiChatService {

    private static final Logger LOGGER = Logger.getLogger(DefaultAiChatService.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Scheduler scheduler;
    private final MessageService messages;
    private final PaperPlatform platform;
    private final Supplier<AiChatConfig> config;
    private final ChatBridge chatBridge;

    private final ArrayDeque<MinecraftChatMessage> chatHistory;
    private final OpenRouterClient openRouterClient;

    public DefaultAiChatService(
            Scheduler scheduler,
            MessageService messages,
            PaperPlatform platform,
            Supplier<AiChatConfig> config,
            ChatBridge chatBridge) {
        this.scheduler = scheduler;
        this.messages = messages;
        this.platform = platform;
        this.config = config;
        this.chatBridge = chatBridge;

        this.chatHistory = new ArrayDeque<>();
        this.openRouterClient = new OpenRouterClient(config.get().apiKey());
    }

    @Override
    public synchronized void chatMessage(MinecraftChatMessage message) {
        chatHistory.addLast(Objects.requireNonNull(message));

        while (chatHistory.size() > config.get().contextMsgCount()) {
            chatHistory.removeFirst();
        }
    }

    @Override
    public void aiRequest(MinecraftChatMessage request) {
        scheduler.runAsync(() -> {
            String response = generateReponse(formatMessage(request));
            scheduler.runOnMain(() -> sendResponse(response));
        });
    }

    /** One chat line, {@code [timestamp] [source] sender: content}, used for the prompt and the history. */
    static String formatMessage(MinecraftChatMessage message) {
        return String.format(
                "[%s] [%s] %s: %s",
                TIME_FORMATTER.format(message.timestamp()),
                message.source(),
                message.senderName(),
                message.content());
    }

    synchronized String formatChatHistory() {
        if (chatHistory.isEmpty()) {
            return "<No recent chat history.>";
        }

        return chatHistory.stream()
                .map(DefaultAiChatService::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    private String generateReponse(String userPrompt) {
        String systemPrompt = systemPrompt();
        String response = "";
        try {
            response = openRouterClient.sendPrompt(config.get().model(), userPrompt, systemPrompt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "AI request interrupted", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "AI request failed", e);
        }

        return response;
    }

    String systemPrompt() {
        AiChatConfig currentConfig = config.get();
        TagResolver placeholders = TagResolver.builder()
                .resolver(TagResolver.resolver(
                        "ai-trigger", Tag.selfClosingInserting(Component.text(currentConfig.aiTrigger()))))
                .resolver(TagResolver.resolver(
                        "always-supported-players",
                        Tag.selfClosingInserting(
                                Component.text(String.join(", ", currentConfig.alwaysSupportedPlayers())))))
                .resolver(TagResolver.resolver(
                        "chat-history", Tag.selfClosingInserting(Component.text(formatChatHistory()))))
                .build();
        return PlainTextComponentSerializer.plainText()
                .serialize(MINI_MESSAGE.deserialize(currentConfig.systemPrompt(), placeholders));
    }

    void sendResponse(String response) {
        if (response.isBlank()) {
            return;
        }

        Component rendered = responseComponent(response);
        platform.server().sendMessage(rendered);
        chatBridge.publish(new ChatBridgeEvent(
                ChatSource.AI, null, "AI Claude", rendered, response, Instant.now()));
    }

    private Component responseComponent(String response) {
        TagResolver placeholders = TagResolver.builder()
                .resolver(TagResolver.resolver("prefix", Tag.selfClosingInserting(messages.render("prefix"))))
                .resolver(TagResolver.resolver("content", Tag.selfClosingInserting(Component.text(response))))
                .build();
        return MINI_MESSAGE.deserialize(config.get().responseFormat(), placeholders);
    }
}
