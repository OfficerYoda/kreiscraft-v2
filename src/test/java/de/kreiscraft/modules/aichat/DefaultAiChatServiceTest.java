package de.kreiscraft.modules.aichat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import de.kreiscraft.shared.chat.ChatSource;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Instant;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class DefaultAiChatServiceTest {

    @Test
    void chatHistory_identifiesPlayerWebAndAiMessages() {
        Supplier<AiChatConfig> config = () -> new AiChatConfig("test-key", "test-model", "@claude", 20);
        DefaultAiChatService service = new DefaultAiChatService(
                new MockScheduler(), mock(MessageService.class), config, new ChatBridge());

        service.chatMessage(new MinecraftChatMessage(
                Instant.parse("2026-10-11T12:00:00Z"), ChatSource.PLAYER, "Alice", "hello"));
        service.chatMessage(new MinecraftChatMessage(
                Instant.parse("2026-10-11T12:01:00Z"), ChatSource.WEB, "WEB", "hi from the website"));
        service.chatMessage(new MinecraftChatMessage(
                Instant.parse("2026-10-11T12:02:00Z"), ChatSource.AI, "AI Claude", "hello back"));

        assertThat(service.formatChatHistory())
                .contains("[PLAYER] Alice: hello")
                .contains("[WEB] WEB: hi from the website")
                .contains("[AI] AI Claude: hello back");
    }

    @Test
    void sendResponse_broadcastsAndPublishesAnAiMessage() {
        MessageService messages = mock(MessageService.class);
        when(messages.render("ai-chat.response", "content", "hello back"))
                .thenReturn(Component.text("AI Claude: hello back"));
        ChatBridge bridge = new ChatBridge();
        java.util.List<ChatBridgeEvent> received = new java.util.ArrayList<>();
        bridge.subscribe(received::add);
        DefaultAiChatService service = new DefaultAiChatService(
                new MockScheduler(), messages, () -> new AiChatConfig("test-key", "test-model", "@claude", 20), bridge);

        service.sendResponse("hello back");

        verify(messages).broadcast("ai-chat.response", "content", "hello back");
        assertThat(received)
                .singleElement()
                .extracting(ChatBridgeEvent::source, ChatBridgeEvent::senderName, ChatBridgeEvent::plainText)
                .containsExactly(ChatSource.AI, "AI Claude", "hello back");
    }
}
