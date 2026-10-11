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
import net.kyori.adventure.text.TextComponent;

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
    public void aiRequest(String requestMsg) {
        scheduler.runAsync(() -> {
            String response = generateReponse(requestMsg);
            scheduler.runOnMain(() -> sendResponse(response));
        });
    }

    synchronized String formatChatHistory() {
        if (chatHistory.isEmpty()) {
            return "<No recent chat history.>";
        }

        return chatHistory.stream()
                .map(msg -> String.format(
                        "[%s] [%s] %s: %s",
                        TIME_FORMATTER.format(msg.timestamp()),
                        msg.source(),
                        msg.senderName(),
                        msg.content()))
                .collect(Collectors.joining("\n"));
    }

    private String generateReponse(String userPrompt) {
        String systemPrompt =
                ((TextComponent) messages.render("ai-chat.system-prompt", "chat-history", formatChatHistory()))
                        .content();
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
