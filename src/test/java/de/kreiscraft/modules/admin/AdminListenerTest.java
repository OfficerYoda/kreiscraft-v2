package de.kreiscraft.modules.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.message.MessageService;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AdminListenerTest {

    private ServerMock server;
    private MessageService messages;
    private AdminListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messages = mock(MessageService.class);
        listener = new AdminListener(messages);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void seed_isCancelledAndMessageSent() {
        PlayerMock player = server.addPlayer("alice");
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/seed");

        listener.onCommand(event);

        assertThat(event.isCancelled()).isTrue();
        verify(messages).send(player, "admin.seed-blocked");
    }

    @Test
    void minecraftSeed_isCancelledAndMessageSent() {
        PlayerMock player = server.addPlayer("alice");
        PlayerCommandPreprocessEvent event =
                new PlayerCommandPreprocessEvent(player, "/minecraft:seed");

        listener.onCommand(event);

        assertThat(event.isCancelled()).isTrue();
        verify(messages).send(player, "admin.seed-blocked");
    }
}
