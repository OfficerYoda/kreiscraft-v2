package de.kreiscraft.modules.startsequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.testsupport.MockScheduler;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultStartSequenceServiceTest {

    @TempDir
    Path tempDir;

    private MockScheduler scheduler;
    private StartSequenceConfig config;
    private MessageService messages;
    private PaperPlatform platform;
    private StartSequenceData data;
    private World world;
    private WorldBorder border;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new MockScheduler();
        config = new StartSequenceConfig(
                Duration.ofSeconds(3), // short countdown for tests
                50.0,
                30_000_000.0,
                Duration.ofSeconds(27_500), // 550000 ticks
                Duration.ofSeconds(15_000), // 300000 ticks
                Duration.ofMillis(7_500), // 150 ticks
                Duration.ofMillis(15_000)); // 300 ticks

        messages = mock(MessageService.class, invocation -> {
            if (Component.class.equals(invocation.getMethod().getReturnType())) {
                return Component.empty();
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });

        player = mock(Player.class);
        Server server = mock(Server.class);
        doReturn(Set.of(player)).when(server).getOnlinePlayers();
        platform = new PaperPlatform(null, server);

        world = mock(World.class);
        border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);

        data = new StartSequenceData(tempDir);
        data.load();
    }

    private DefaultStartSequenceService createService() {
        return new DefaultStartSequenceService(scheduler, config, messages, platform, data);
    }

    @Test
    void hasStarted_whenNotTriggered_returnsFalse() {
        DefaultStartSequenceService svc = createService();
        assertThat(svc.hasStarted()).isFalse();
    }

    @Test
    void trigger_whenNotStarted_showsFirstCountdownNumberAfterDecrement() {
        DefaultStartSequenceService svc = createService();

        svc.trigger(world);
        scheduler.advance(Duration.ZERO);

        // RusticPrism decrements before displaying: a countdown of 3 first shows 2.
        verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "2");
        verify(messages, never())
                .renderFor(player, "start-sequence.countdown-title", "seconds", "3");
    }

    @Test
    void trigger_whenAlreadyStarted_throwsIllegalState() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        assertThatThrownBy(() -> svc.trigger(world)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void trigger_showsCountdownNumbersInDescendingOrderThenStart() {
        DefaultStartSequenceService svc = createService();

        svc.trigger(world);
        scheduler.advance(Duration.ofSeconds(2));

        InOrder order = inOrder(messages);
        order.verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "2");
        order.verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "1");
        order.verify(messages).renderFor(player, "start-sequence.started");
        order.verify(messages).renderFor(player, "start-sequence.started-subtitle");
    }

    @Test
    void trigger_countdownAndStartPresentation_matchRusticPrism() {
        when(messages.renderFor(player, "start-sequence.started")).thenReturn(Component.text("started"));
        when(messages.renderFor(player, "start-sequence.started-subtitle"))
                .thenReturn(Component.text("have-fun"));

        DefaultStartSequenceService svc = createService();
        svc.trigger(world);
        scheduler.advance(Duration.ofSeconds(2));

        ArgumentCaptor<Title> titleCaptor = ArgumentCaptor.forClass(Title.class);
        verify(player, times(3)).showTitle(titleCaptor.capture());
        List<Title> titles = titleCaptor.getAllValues();

        // Countdown titles stay for 2 seconds with no fade and no subtitle.
        assertThat(titles.get(0).times())
                .isEqualTo(Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO));
        assertThat(titles.get(1).times())
                .isEqualTo(Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO));
        assertThat(titles.get(0).subtitle()).isEqualTo(Component.empty());

        // Start title carries the "Have fun" subtitle and stays for 4 seconds.
        assertThat(titles.get(2).title()).isEqualTo(Component.text("started"));
        assertThat(titles.get(2).subtitle()).isEqualTo(Component.text("have-fun"));
        assertThat(titles.get(2).times())
                .isEqualTo(Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ZERO));

        ArgumentCaptor<Sound> soundCaptor = ArgumentCaptor.forClass(Sound.class);
        verify(player, times(3)).playSound(soundCaptor.capture());
        List<Sound> sounds = soundCaptor.getAllValues();

        assertThat(sounds.get(0).name())
                .isEqualTo(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP.key());
        assertThat(sounds.get(0).source()).isEqualTo(Sound.Source.PLAYER);
        assertThat(sounds.get(0).volume()).isEqualTo(100f);
        assertThat(sounds.get(0).pitch()).isEqualTo(100f);

        assertThat(sounds.get(2).name())
                .isEqualTo(org.bukkit.Sound.ITEM_GOAT_HORN_SOUND_2.key());
        assertThat(sounds.get(2).source()).isEqualTo(Sound.Source.PLAYER);
        assertThat(sounds.get(2).volume()).isEqualTo(200f);
        assertThat(sounds.get(2).pitch()).isEqualTo(1f);
    }

    @Test
    void trigger_whenCountdownCompletes_setsBorderPhase1InTicks() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        scheduler.advance(Duration.ofSeconds(2));

        // 550000 ticks are passed straight through to Bukkit's tick-based WorldBorder API.
        verify(border).changeSize(30_000_000.0, 550_000L);
    }

    @Test
    void trigger_whenPhase1To2DelayPasses_setsBorderPhase2InTicks() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        scheduler.advance(Duration.ofSeconds(2));
        scheduler.advance(Duration.ofMillis(7_500));

        // 300000 ticks are passed straight through to Bukkit's tick-based WorldBorder API.
        verify(border).changeSize(30_000_000.0, 300_000L);
    }

    @Test
    void trigger_whenPhase2ToInstantDelayPasses_setsBorderInstant() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        scheduler.advance(Duration.ofSeconds(2));
        scheduler.advance(Duration.ofMillis(15_000));

        verify(border).setSize(30_000_000.0);
    }

    @Test
    void trigger_withNoPlayersOnline_completesBorderSequenceSafely() {
        Server emptyServer = mock(Server.class);
        doReturn(Collections.emptySet()).when(emptyServer).getOnlinePlayers();
        DefaultStartSequenceService svc = new DefaultStartSequenceService(
                scheduler, config, messages, new PaperPlatform(null, emptyServer), data);

        svc.trigger(world);
        scheduler.advance(Duration.ofSeconds(2));
        scheduler.advance(Duration.ofMillis(15_000));

        verify(border).changeSize(30_000_000.0, 550_000L);
        verify(border).setSize(30_000_000.0);
    }

    @Test
    void reset_whenRunning_cancelsPendingBorderExpansion() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        svc.reset();

        // Advance well past the countdown — changeSize (gradual expansion) should NOT fire
        scheduler.advance(Duration.ofSeconds(60));
        verify(border, never()).changeSize(any(double.class), any(long.class));
        // The instant-set task should also not fire (cancelled)
        verify(border, never()).setSize(30_000_000.0);
    }

    @Test
    void reset_resetsBorderToInitialSize() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        svc.reset();

        verify(border).setSize(50.0);
    }

    @Test
    void reset_setsHasStartedToFalse() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);
        assertThat(svc.hasStarted()).isTrue();

        svc.reset();
        assertThat(svc.hasStarted()).isFalse();
    }

    @Test
    void reset_persistsResetState() throws Exception {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);
        svc.reset();
        data.flushIfDirty();

        StartSequenceData reloaded = new StartSequenceData(tempDir);
        reloaded.load();

        assertThat(reloaded.hasStarted()).isFalse();
    }

    @Test
    void trigger_afterReset_succeeds() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);
        svc.reset();

        // Should not throw after reset
        svc.trigger(world);
        assertThat(svc.hasStarted()).isTrue();
    }

    @Test
    void trigger_persistsStartedStateAcrossRestart() throws Exception {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);
        data.flushIfDirty();

        StartSequenceData reloaded = new StartSequenceData(tempDir);
        reloaded.load();
        assertThat(reloaded.hasStarted()).isTrue();

        DefaultStartSequenceService restarted =
                new DefaultStartSequenceService(scheduler, config, messages, platform, reloaded);
        assertThat(restarted.hasStarted()).isTrue();
        assertThatThrownBy(() -> restarted.trigger(world)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shutdown_cancelsPendingTasks() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        svc.shutdown();

        scheduler.advance(Duration.ofSeconds(60));
        verify(border, never()).changeSize(any(double.class), any(long.class));
        // Shutdown does not reset border, so no setSize calls expected at all
        verify(border, never()).setSize(any(double.class));
    }
}
