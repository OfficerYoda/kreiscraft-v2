package de.kreiscraft.modules.reboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
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
        when(messages.render(any(), any(Object[].class))).thenReturn(Component.empty());
        when(messages.renderFor(any(CommandSender.class), anyString(), any(Object[].class)))
                .thenReturn(Component.empty());
        service = new DefaultRebootService(scheduler, messages, platform, () -> config);
    }

    @Test
    void isRunning_whenNotStarted_returnsFalse() {
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void start_setsRunningAndAnnouncesToEachPlayer() {
        Player player = mock(Player.class);
        when(server.getOnlinePlayers()).thenAnswer(inv -> List.of(player));
        service.start(Duration.ofSeconds(10), "test", "alice");
        assertThat(service.isRunning()).isTrue();
        verify(messages)
                .send(
                        eq(player),
                        eq("reboot.started"),
                        eq("seconds"),
                        eq("10"),
                        eq("reason"),
                        eq("test"));
    }

    @Test
    void start_withoutReason_announcesTheNoReasonPlaceholder() {
        Player player = mock(Player.class);
        when(server.getOnlinePlayers()).thenAnswer(inv -> List.of(player));
        Component noReason = Component.text("<no reason>");
        doReturn(noReason)
                .when(messages)
                .renderFor(eq(player), eq("reboot.no-reason"), any(Object[].class));

        service.start(Duration.ofSeconds(10), "", "alice");

        verify(messages)
                .send(
                        eq(player),
                        eq("reboot.started"),
                        eq("seconds"),
                        eq("10"),
                        eq("reason"),
                        eq(noReason));
    }

    @Test
    void start_andAdvance5s_remaining4s() {
        service.start(Duration.ofSeconds(10), "test", "alice");
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(service.remaining()).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void start_whenAlreadyRunning_withLargerValue_doesNotReplace() {
        service.start(Duration.ofSeconds(30), "first", "alice");
        scheduler.advance(Duration.ofSeconds(5));
        long remainingBefore = service.remaining().getSeconds();
        service.start(Duration.ofSeconds(60), "second", "bob");
        assertThat(service.remaining().getSeconds()).isEqualTo(remainingBefore);
    }

    @Test
    void start_whenAlreadyRunning_withSmallerValue_shortensCountdown() {
        service.start(Duration.ofSeconds(30), "first", "alice");
        scheduler.advance(Duration.ofSeconds(5)); // remaining ~25
        service.start(Duration.ofSeconds(10), "shorter", "alice");
        assertThat(service.remaining()).isEqualTo(Duration.ofSeconds(10));
        assertThat(service.isRunning()).isTrue();
    }

    @Test
    void cancel_whenRunning_stopsAndBroadcasts() {
        service.start(Duration.ofSeconds(60), "test", "alice");
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
    void tick_announcesStartingSecondBeforeDecrementing() {
        // Start at 9: the first tick should announce "9" (before decrement), not "8"
        service.start(Duration.ofSeconds(9), "test", "alice");
        scheduler.advance(Duration.ofSeconds(1));
        verify(messages).broadcast(eq("reboot.countdown-chat"), eq("seconds"), eq("9"));
    }

    @Test
    void tick_atZero_kicksAllWithRestartCauseAndShutsDown() {
        Player player = mock(Player.class);
        Component kickComponent = Component.text("Server restarting");
        doReturn(kickComponent)
                .when(messages)
                .renderFor(eq(player), eq("reboot.kick-message"), any(Object[].class));
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        service.start(Duration.ofSeconds(1), "test", "alice");
        scheduler.advance(Duration.ofSeconds(1));

        assertThat(service.isRunning()).isFalse();
        verify(player).kick(kickComponent, PlayerKickEvent.Cause.RESTART_COMMAND);
        verify(server).shutdown();
    }

    @Test
    void tick_atZero_rendersKickMessageWithReasonInitiatorWebsite() {
        Player player = mock(Player.class);
        doReturn(Component.empty())
                .when(messages)
                .renderFor(eq(player), eq("reboot.kick-message"), any(Object[].class));
        when(server.getOnlinePlayers()).thenAnswer(inv -> List.of(player));

        service.start(Duration.ofSeconds(1), "planned", "alice");
        scheduler.advance(Duration.ofSeconds(2));

        verify(messages).renderFor(
                eq(player),
                eq("reboot.kick-message"),
                eq("reason"), eq("planned"),
                eq("initiator"), eq("alice"),
                eq("website"), eq("kreiscraft.de"));
    }

    @Test
    void tick_atTitleSecond_showsTitleWithReasonSubtitle() {
        Player player = mock(Player.class);
        when(server.getOnlinePlayers()).thenAnswer(inv -> List.of(player));
        service.start(Duration.ofSeconds(10), "maintenance", "alice");
        scheduler.advance(Duration.ofSeconds(1));

        // 10 is in titleSeconds; subtitle should be rendered with the reason
        verify(messages).renderFor(eq(player), eq("reboot.countdown-subtitle"), eq("reason"),
                eq("maintenance"), eq("seconds"), eq("10"));
    }

    @Test
    void tick_givesOneAFullSecond_thenSendsZeroAndKicks() {
        Player player = mock(Player.class);
        when(server.getOnlinePlayers()).thenAnswer(inv -> List.of(player));
        config = new RebootConfig(Duration.ofSeconds(60), List.of(2, 1, 0), List.of());
        service = new DefaultRebootService(scheduler, messages, platform, () -> config);

        service.start(Duration.ofSeconds(2), "maintenance", "alice");
        scheduler.advance(Duration.ofSeconds(1));

        verify(messages).renderFor(
                eq(player),
                eq("reboot.countdown-title"),
                eq("seconds"),
                eq("1"),
                eq("reason"),
                eq("maintenance"));
        verify(player, never()).kick(any(Component.class), eq(PlayerKickEvent.Cause.RESTART_COMMAND));

        scheduler.advance(Duration.ofSeconds(1));

        verify(messages).renderFor(
                eq(player),
                eq("reboot.countdown-title"),
                eq("seconds"),
                eq("0"),
                eq("reason"),
                eq("maintenance"));
        verify(player).kick(any(Component.class), eq(PlayerKickEvent.Cause.RESTART_COMMAND));
        verify(server).shutdown();
    }

    @Test
    void tick_atTitleSecond_withoutReason_usesNoReasonPlaceholderInSubtitle() {
        Player player = mock(Player.class);
        when(server.getOnlinePlayers()).thenAnswer(inv -> List.of(player));
        Component noReason = Component.text("<no reason>");
        doReturn(noReason)
                .when(messages)
                .renderFor(eq(player), eq("reboot.no-reason"), any(Object[].class));

        service.start(Duration.ofSeconds(10), "", "alice");
        scheduler.advance(Duration.ofSeconds(1));

        verify(messages)
                .renderFor(
                        eq(player),
                        eq("reboot.countdown-subtitle"),
                        eq("reason"),
                        eq(noReason),
                        eq("seconds"),
                        eq("10"));
    }

    @Test
    void tick_atZero_withoutReason_usesNoReasonPlaceholderInKickMessage() {
        Player player = mock(Player.class);
        when(server.getOnlinePlayers()).thenAnswer(inv -> List.of(player));
        Component noReason = Component.text("<no reason>");
        doReturn(noReason)
                .when(messages)
                .renderFor(eq(player), eq("reboot.no-reason"), any(Object[].class));

        service.start(Duration.ofSeconds(1), "", "alice");
        scheduler.advance(Duration.ofSeconds(2));

        verify(messages)
                .renderFor(
                        eq(player),
                        eq("reboot.kick-message"),
                        eq("reason"),
                        eq(noReason),
                        eq("initiator"),
                        eq("alice"),
                        eq("website"),
                        eq("kreiscraft.de"));
    }

    @Test
    void cancel_stopsCountdown() {
        service.start(Duration.ofSeconds(30), "test", "alice");
        scheduler.advance(Duration.ofSeconds(5));
        service.cancel();
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(service.isRunning()).isFalse();
        assertThat(service.remaining().getSeconds()).isEqualTo(0L);
    }

    @Test
    void shutdown_stopsRunningTask() {
        service.start(Duration.ofSeconds(60), "test", "alice");
        assertThat(service.isRunning()).isTrue();
        service.shutdown();
        assertThat(service.isRunning()).isFalse();
    }
}
