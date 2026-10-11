package de.kreiscraft.modules.aichat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import de.kreiscraft.shared.chat.ChatSource;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Instant;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

class DefaultAiChatServiceTest {

    @Test
    void chatHistory_identifiesPlayerWebAndAiMessages() {
        Supplier<AiChatConfig> config =
                () -> new AiChatConfig("test-key", "test-model", "", "", "@claude", 20, null);
        DefaultAiChatService service = new DefaultAiChatService(
                new MockScheduler(), mock(MessageService.class), unusedPlatform(), config, new ChatBridge());

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
    void systemPrompt_expandsConfiguredPlaceholdersAsLiteralText() {
        String template = "Trigger: <ai-trigger>; protected: <always-supported-players>; history: <chat-history>";
        DefaultAiChatService service = new DefaultAiChatService(
                new MockScheduler(),
                mock(MessageService.class),
                unusedPlatform(),
                () -> new AiChatConfig(
                        "test-key",
                        "test-model",
                        template,
                        "",
                        "<red>@bot",
                        20,
                        java.util.List.of("<green>Steve")),
                new ChatBridge());
        service.chatMessage(new MinecraftChatMessage(
                Instant.parse("2026-10-11T12:00:00Z"), ChatSource.PLAYER, "Alice", "<blue>hello"));

        assertThat(service.systemPrompt())
                .contains("Trigger: <red>@bot")
                .contains("protected: <green>Steve")
                .contains("[PLAYER] Alice: <blue>hello");
    }

    @Test
    void sendResponse_broadcastsAndPublishesAnAiMessage() {
        MessageService messages = mock(MessageService.class);
        when(messages.render("prefix")).thenReturn(Component.empty());
        Server server = mock(Server.class);
        PaperPlatform platform = new PaperPlatform(null, server);
        ChatBridge bridge = new ChatBridge();
        java.util.List<ChatBridgeEvent> received = new java.util.ArrayList<>();
        bridge.subscribe(received::add);
        DefaultAiChatService service = new DefaultAiChatService(
                new MockScheduler(),
                messages,
                platform,
                () -> new AiChatConfig("test-key", "test-model", "", "AI: <content>", "@claude", 20, null),
                bridge);

        service.sendResponse("hello back");

        verify(server).sendMessage(Component.text("AI: hello back"));
        assertThat(received)
                .singleElement()
                .extracting(ChatBridgeEvent::source, ChatBridgeEvent::senderName, ChatBridgeEvent::plainText)
                .containsExactly(ChatSource.AI, "AI Claude", "hello back");
        assertThat(PlainTextComponentSerializer.plainText().serialize(received.getFirst().rendered()))
                .isEqualTo("AI: hello back");
    }

    private static PaperPlatform unusedPlatform() {
        return new PaperPlatform(null, null);
    }
}
