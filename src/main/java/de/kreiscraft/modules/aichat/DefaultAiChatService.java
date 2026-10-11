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
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import de.kreiscraft.shared.chat.ChatSource;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class DefaultAiChatService implements AiChatService {

    private static final Logger LOGGER = Logger.getLogger(DefaultAiChatService.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Scheduler scheduler;
    private final MessageService messages;
    private final Supplier<AiChatConfig> config;
    private final ChatBridge chatBridge;

    private final ArrayDeque<MinecraftChatMessage> chatHistory;
    private final OpenRouterClient openRouterClient;

    public DefaultAiChatService(
            Scheduler scheduler, MessageService messages, Supplier<AiChatConfig> config, ChatBridge chatBridge) {
        this.scheduler = scheduler;
        this.messages = messages;
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
        return PlainTextComponentSerializer.plainText().serialize(messages.render(
                "ai-chat.system-prompt",
                "ai-trigger",
                Component.text(currentConfig.aiTrigger()),
                "always-supported-players",
                Component.text(String.join(", ", currentConfig.alwaysSupportedPlayers())),
                "chat-history",
                Component.text(formatChatHistory())));
    }

    void sendResponse(String response) {
        if (response.isBlank()) {
            return;
        }

        messages.broadcast("ai-chat.response", "content", response);
        chatBridge.publish(new ChatBridgeEvent(
                ChatSource.AI,
                null,
                "AI Claude",
                messages.render("ai-chat.response", "content", response),
                response,
                Instant.now()));
    }
}
