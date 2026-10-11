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
import net.kyori.adventure.text.TextComponent;

public final class DefaultAiChatService implements AiChatService {

    private static final Logger LOGGER = Logger.getLogger(DefaultAiChatService.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Scheduler scheduler;
    private final MessageService messages;
    private final Supplier<AiChatConfig> config;

    private final ArrayDeque<MinecraftChatMessage> chatHistory;
    private final OpenRouterClient openRouterClient;

    public DefaultAiChatService(Scheduler scheduler, MessageService messages, Supplier<AiChatConfig> config) {
        this.scheduler = scheduler;
        this.messages = messages;
        this.config = config;

        this.chatHistory = new ArrayDeque<>();
        this.openRouterClient = new OpenRouterClient(config.get().apiKey());
    }

    @Override
    public void chatMessage(String message) {
        MinecraftChatMessage mccMsg = new MinecraftChatMessage(Instant.now(), message);
        chatHistory.addLast(mccMsg);

        if (chatHistory.size() > config.get().contextMsgCount()) {
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

    private String formatChatHistory() {
        if (chatHistory.isEmpty()) {
            return "<No recent chat history.>";
        }

        return chatHistory.stream()
                .map(msg -> String.format("[%s] %s", TIME_FORMATTER.format(msg.timestamp()), msg.content()))
                .collect(Collectors.joining("\n"));
    }

    private String generateReponse(String userPrompt) {
        String systemPrompt = ((TextComponent) messages.render("ai-chat.system-prompt", "chat-history", formatChatHistory())).content();
        System.out.println("System prompt: " + systemPrompt);

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

    private void sendResponse(String response) {
        if (response.isBlank()) {
            return;
        }

        messages.broadcast("ai-chat.response", "content", response);
    }
}
