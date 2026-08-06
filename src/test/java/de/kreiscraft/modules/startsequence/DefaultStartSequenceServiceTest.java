package de.kreiscraft.modules.startsequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.testsupport.MockScheduler;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new MockScheduler();
        config = new StartSequenceConfig(
                Duration.ofSeconds(3), // short countdown for tests
                50.0,
                30_000_000.0,
                Duration.ofSeconds(550_000),
                Duration.ofSeconds(300_000),
                Duration.ofMillis(7_500),
                Duration.ofMillis(15_000));

        messages = mock(MessageService.class);
        when(messages.render(anyString(), any())).thenReturn(Component.empty());
        when(messages.render(anyString())).thenReturn(Component.empty());

        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenReturn(Collections.emptySet());
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
    void trigger_whenNotStarted_schedulesCountdownTasks() {
        DefaultStartSequenceService svc = createService();

        svc.trigger(world);

        // Advance 0ms — first runLater(0s) fires showing countdown title for tick=3
        scheduler.advance(Duration.ZERO);
        verify(messages).render("start-sequence.countdown-title", "seconds", "3");
    }

    @Test
    void trigger_whenAlreadyStarted_throwsIllegalState() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        assertThatThrownBy(() -> svc.trigger(world))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reset_whenRunning_cancelsPendingBorderExpansion() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        svc.reset();

        // Advance well past the countdown — changeSize (gradual expansion) should NOT fire
        scheduler.advance(Duration.ofSeconds(60));
        verify(border, never()).changeSize(any(double.class), any(long.class));
        // The instant-set task should also not fire (cancelled); only reset's own setSize(50.0) was called
        verify(border, never()).setSize(30_000_000.0);
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
    void trigger_whenCountdownCompletes_setsBorderPhase1() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        scheduler.advance(Duration.ofSeconds(3));

        verify(border).changeSize(30_000_000.0, 550_000L);
    }

    @Test
    void trigger_whenPhase1To2DelayPasses_setsBorderPhase2() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        scheduler.advance(Duration.ofSeconds(3));
        scheduler.advance(Duration.ofMillis(7_500));

        verify(border).changeSize(30_000_000.0, 300_000L);
    }

    @Test
    void trigger_whenPhase2ToInstantDelayPasses_setsBorderInstant() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        scheduler.advance(Duration.ofSeconds(3));
        scheduler.advance(Duration.ofMillis(15_000));

        verify(border).setSize(30_000_000.0);
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

    @Test
    void reset_setsHasStartedToFalse() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);
        assertThat(svc.hasStarted()).isTrue();

        svc.reset();
        assertThat(svc.hasStarted()).isFalse();
    }
}
