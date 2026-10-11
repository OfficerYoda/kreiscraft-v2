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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultAiChatServiceTest {

    @Test
    void chatHistory_identifiesPlayerWebAndAiMessages() {
        Supplier<AiChatConfig> config = () -> new AiChatConfig("test-key", "test-model", "@claude", 20, null);
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
    void userPrompt_includesTimestampAndSender() {
        MinecraftChatMessage request = new MinecraftChatMessage(
                Instant.parse("2026-10-11T12:00:00Z"), ChatSource.PLAYER, "Alice", "hello");

        assertThat(DefaultAiChatService.formatMessage(request))
                .matches("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\] \\[PLAYER\\] Alice: hello");
    }

    @Test
    void systemPrompt_suppliesTriggerProtectedPlayersAndChatHistoryAsLiteralComponents() {
        MessageService messages = mock(MessageService.class);
        when(messages.render(org.mockito.ArgumentMatchers.eq("ai-chat.system-prompt"),
                org.mockito.ArgumentMatchers.any(Object[].class)))
                        .thenReturn(Component.text("rendered prompt"));
        DefaultAiChatService service = new DefaultAiChatService(
                new MockScheduler(),
                messages,
                () -> new AiChatConfig("test-key", "test-model", "@bot", 20, java.util.List.of("SteveGamer42")),
                new ChatBridge());
        service.chatMessage(new MinecraftChatMessage(
                Instant.parse("2026-10-11T12:00:00Z"), ChatSource.PLAYER, "Alice", "hello"));

        assertThat(service.systemPrompt()).isEqualTo("rendered prompt");

        ArgumentCaptor<Object[]> placeholders = ArgumentCaptor.forClass(Object[].class);
        verify(messages).render(org.mockito.ArgumentMatchers.eq("ai-chat.system-prompt"), placeholders.capture());
        assertThat(placeholders.getValue()).hasSize(6);
        assertThat(placeholders.getValue()[0]).isEqualTo("ai-trigger");
        assertThat(placeholders.getValue()[2]).isEqualTo("always-supported-players");
        assertThat(placeholders.getValue()[4]).isEqualTo("chat-history");
        assertThat(PlainTextComponentSerializer.plainText()
                .serialize((Component) placeholders.getValue()[1]))
                        .isEqualTo("@bot");
        assertThat(PlainTextComponentSerializer.plainText()
                .serialize((Component) placeholders.getValue()[3]))
                        .isEqualTo("SteveGamer42");
        assertThat(PlainTextComponentSerializer.plainText()
                .serialize((Component) placeholders.getValue()[5]))
                        .contains("[PLAYER] Alice: hello");
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
                new MockScheduler(),
                messages,
                () -> new AiChatConfig("test-key", "test-model", "@claude", 20, null),
                bridge);

        service.sendResponse("hello back");

        verify(messages).broadcast("ai-chat.response", "content", "hello back");
        assertThat(received)
                .singleElement()
                .extracting(ChatBridgeEvent::source, ChatBridgeEvent::senderName, ChatBridgeEvent::plainText)
                .containsExactly(ChatSource.AI, "AI Claude", "hello back");
    }
}
