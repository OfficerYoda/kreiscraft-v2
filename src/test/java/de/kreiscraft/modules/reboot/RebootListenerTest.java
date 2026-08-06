package de.kreiscraft.modules.reboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class RebootListenerTest {

    private ServerMock server;
    private MessageService messages;
    private RebootListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messages = mock(MessageService.class);
        listener = new RebootListener(messages);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerCommandPreprocessEvent fireEvent(Player player, String command) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, command);
        listener.onCommand(event);
        return event;
    }

    @Test
    void seed_isCancelledAndMessageSent() {
        PlayerMock player = server.addPlayer("alice");
        PlayerCommandPreprocessEvent event = fireEvent(player, "/seed");
        assertThat(event.isCancelled()).isTrue();
        verify(messages).send(player, "reboot.seed-blocked");
    }

    @Test
    void minecraftSeed_isCancelled() {
        PlayerMock player = server.addPlayer("alice");
        PlayerCommandPreprocessEvent event = fireEvent(player, "/minecraft:seed");
        assertThat(event.isCancelled()).isTrue();
        verify(messages).send(player, "reboot.seed-blocked");
    }

    @Test
    void stop_isRewrittenToReboot() {
        PlayerMock player = server.addPlayer("alice");
        PlayerCommandPreprocessEvent event = fireEvent(player, "/stop");
        assertThat(event.isCancelled()).isFalse();
        assertThat(event.getMessage()).isEqualTo("/reboot");
    }

    @Test
    void restart_isRewrittenToReboot() {
        PlayerMock player = server.addPlayer("alice");
        PlayerCommandPreprocessEvent event = fireEvent(player, "/restart");
        assertThat(event.getMessage()).isEqualTo("/reboot");
    }

    @Test
    void spigotRestart_isRewrittenToReboot() {
        PlayerMock player = server.addPlayer("alice");
        PlayerCommandPreprocessEvent event = fireEvent(player, "/spigot:restart");
        assertThat(event.getMessage()).isEqualTo("/reboot");
    }

    @Test
    void minecraftStop_isRewrittenToReboot() {
        PlayerMock player = server.addPlayer("alice");
        PlayerCommandPreprocessEvent event = fireEvent(player, "/minecraft:stop");
        assertThat(event.getMessage()).isEqualTo("/reboot");
    }

    @Test
    void otherCommand_isNotAffected() {
        PlayerMock player = server.addPlayer("alice");
        PlayerCommandPreprocessEvent event = fireEvent(player, "/gamemode survival");
        assertThat(event.isCancelled()).isFalse();
        assertThat(event.getMessage()).isEqualTo("/gamemode survival");
    }
}
