package de.kreiscraft.modules.reboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultRebootServiceTest {

    @Mock
    private MessageService messages;

    @Mock
    private PaperPlatform platform;

    @Mock
    private Server server;

    private MockScheduler scheduler;
    private RebootConfig config;
    private DefaultRebootService service;

    @BeforeEach
    void setUp() {
        scheduler = new MockScheduler();
        config = new RebootConfig(
                Duration.ofSeconds(60), List.of(500, 60, 30, 10), List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1));
        when(platform.server()).thenReturn(server);
        when(server.getOnlinePlayers()).thenAnswer(inv -> List.of());
        service = new DefaultRebootService(scheduler, messages, platform, config);
    }

    @Test
    void isRunning_whenNotStarted_returnsFalse() {
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void start_setsRunningAndBroadcasts() {
        service.start(Duration.ofSeconds(10), "test");
        assertThat(service.isRunning()).isTrue();
        verify(messages).broadcast(eq("reboot.started"), eq("seconds"), eq("10"), eq("reason"), eq("test"));
    }

    @Test
    void start_andAdvance5s_remaining5s() {
        service.start(Duration.ofSeconds(10), "test");
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(service.remaining()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void start_whenAlreadyRunning_withLargerValue_doesNotReplace() {
        service.start(Duration.ofSeconds(30), "first");
        scheduler.advance(Duration.ofSeconds(5));
        long remainingBefore = service.remaining().getSeconds();
        service.start(Duration.ofSeconds(60), "second");
        assertThat(service.remaining().getSeconds()).isEqualTo(remainingBefore);
    }

    @Test
    void start_whenAlreadyRunning_withSmallerValue_shortensCountdown() {
        service.start(Duration.ofSeconds(30), "first");
        scheduler.advance(Duration.ofSeconds(5)); // remaining ~25
        service.start(Duration.ofSeconds(10), "shorter");
        assertThat(service.remaining()).isEqualTo(Duration.ofSeconds(10));
        assertThat(service.isRunning()).isTrue();
    }

    @Test
    void cancel_whenRunning_stopsAndBroadcasts() {
        service.start(Duration.ofSeconds(60), "test");
        service.cancel();
        assertThat(service.isRunning()).isFalse();
        verify(messages).broadcast("reboot.cancelled");
    }

    @Test
    void cancel_whenNotRunning_doesNothingAndNoBroadcast() {
        service.cancel();
        assertThat(service.isRunning()).isFalse();
        verify(messages, never()).broadcast("reboot.cancelled");
    }

    @Test
    void tick_atChatSecond_broadcastsCountdownChat() {
        // Start at 9: after 1 tick remaining=8, which is in chatSeconds but NOT in titleSeconds
        service.start(Duration.ofSeconds(9), "test");
        scheduler.advance(Duration.ofSeconds(1));
        verify(messages).broadcast(eq("reboot.countdown-chat"), eq("seconds"), eq("8"));
    }

    @Test
    void tick_atZero_kicksAllWithLangKeyAndShutsDown() {
        Player player = mock(Player.class);
        Component kickComponent = Component.text("Server restarting");
        // Stub render to return a distinct component so we can assert it was used for kick
        when(messages.render("reboot.kick-message")).thenReturn(kickComponent);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        service.start(Duration.ofSeconds(1), "test");
        scheduler.advance(Duration.ofSeconds(1));

        assertThat(service.isRunning()).isFalse();
        // Verify kick used the component rendered from the lang key, not a hard-coded string
        verify(player).kick(kickComponent);
        verify(server).shutdown();
    }

    @Test
    void tick_atZero_rendersKickMessageFromLangKey() {
        when(messages.render("reboot.kick-message")).thenReturn(Component.empty());
        when(server.getOnlinePlayers()).thenAnswer(inv -> List.of());

        service.start(Duration.ofSeconds(1), "test");
        scheduler.advance(Duration.ofSeconds(1));

        // Verify the lang key is used, not a hard-coded string
        verify(messages).render("reboot.kick-message");
    }

    @Test
    void cancel_stopsCountdown() {
        service.start(Duration.ofSeconds(30), "test");
        scheduler.advance(Duration.ofSeconds(5));
        service.cancel();
        scheduler.advance(Duration.ofSeconds(10));
        // After cancel, isRunning is false and remaining is reset
        assertThat(service.isRunning()).isFalse();
        assertThat(service.remaining().getSeconds()).isEqualTo(0L);
    }

    @Test
    void shutdown_stopsRunningTask() {
        service.start(Duration.ofSeconds(60), "test");
        assertThat(service.isRunning()).isTrue();
        service.shutdown();
        assertThat(service.isRunning()).isFalse();
    }
}
