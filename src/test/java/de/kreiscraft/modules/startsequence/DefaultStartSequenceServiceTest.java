package de.kreiscraft.modules.startsequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.config.YamlConfigService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.testsupport.MockScheduler;
import io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Verifies the Start Sequence's public behaviour: the full countdown and the single world-border
 * transition delegated to Bukkit. The runtime Bukkit seam is MockBukkit; the border transition is
 * observed through Paper's {@link WorldBorderBoundsChangeEvent} on the real {@link WorldMock} border.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultStartSequenceServiceTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private WorldMock world;
    private Player player;
    private MockScheduler scheduler;
    private StartSequenceConfig config;
    private MessageService messages;
    private PaperPlatform platform;
    private YamlConfigService configService;
    private final List<WorldBorderBoundsChangeEvent> borderChanges = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");

        Plugin plugin = MockBukkit.createMockPlugin();
        server.getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            public void onBorderChange(WorldBorderBoundsChangeEvent event) {
                                borderChanges.add(event);
                                // The plugin's delegated transition is a STARTED_MOVE; cancel it so
                                // MockBukkit does not schedule a 600k-tick move. Instant size changes
                                // (reset) are allowed through.
                                if (event.getType() == WorldBorderBoundsChangeEvent.Type.STARTED_MOVE) {
                                    event.setCancelled(true);
                                }
                            }
                        },
                        plugin);

        player = mock(Player.class);
        Server playerServer = mock(Server.class);
        doReturn(Set.of(player)).when(playerServer).getOnlinePlayers();
        platform = new PaperPlatform(null, playerServer);

        scheduler = new MockScheduler();
        config = new StartSequenceConfig(
                Duration.ofSeconds(3), 50.0, 30_000_000.0, Duration.ofSeconds(30_000));
        messages = mock(MessageService.class, invocation -> {
            if (Component.class.equals(invocation.getMethod().getReturnType())) {
                return Component.empty();
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });

        configService = new YamlConfigService(tempDir, getClass().getClassLoader());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private DefaultStartSequenceService createService() {
        return new DefaultStartSequenceService(scheduler, config, messages, platform, configService);
    }

    private YamlConfigService reopenConfig() throws IOException {
        return new YamlConfigService(tempDir, getClass().getClassLoader());
    }

    private List<WorldBorderBoundsChangeEvent> startedMoves() {
        return borderChanges.stream()
                .filter(e -> e.getType() == WorldBorderBoundsChangeEvent.Type.STARTED_MOVE)
                .toList();
    }

    @Test
    void hasStarted_whenNotTriggered_returnsFalse() {
        DefaultStartSequenceService svc = createService();
        assertThat(svc.hasStarted()).isFalse();
    }

    @Test
    void trigger_whenNotStarted_rendersConfiguredCountdownThenCountsDown() {
        DefaultStartSequenceService svc = createService();

        svc.trigger(world);
        scheduler.advance(Duration.ZERO);

        // A countdown of 3 renders 3, then 2, then 1 — the full promised range.
        verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "3");
        scheduler.advance(Duration.ofSeconds(1));
        verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "2");
        scheduler.advance(Duration.ofSeconds(1));
        verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "1");
        verify(messages, never()).renderFor(player, "start-sequence.countdown-title", "seconds", "0");

        // The start title follows one second after the final number.
        scheduler.advance(Duration.ofSeconds(1));
        verify(messages).renderFor(player, "start-sequence.started");
        verify(messages).renderFor(player, "start-sequence.started-subtitle");
    }

    @Test
    void trigger_defaultCountdown_rendersTenThroughOneBeforeStarting() {
        config = new StartSequenceConfig(null, null, null, null);
        DefaultStartSequenceService svc = createService();

        svc.trigger(world);
        scheduler.advance(Duration.ZERO);

        verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "10");
        verify(messages, never())
                .renderFor(player, "start-sequence.countdown-title", "seconds", "11");

        scheduler.advance(Duration.ofSeconds(9));

        verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "1");
        verify(messages, times(10))
                .renderFor(
                        eq(player),
                        eq("start-sequence.countdown-title"),
                        eq("seconds"),
                        anyString());
        verify(messages, never()).renderFor(player, "start-sequence.started");

        scheduler.advance(Duration.ofSeconds(1));
        verify(messages).renderFor(player, "start-sequence.started");
    }

    @Test
    void trigger_rendersCountdownInDescendingOrderThenStart() {
        DefaultStartSequenceService svc = createService();

        svc.trigger(world);
        scheduler.advance(Duration.ofSeconds(3));

        InOrder order = inOrder(messages);
        order.verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "3");
        order.verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "2");
        order.verify(messages).renderFor(player, "start-sequence.countdown-title", "seconds", "1");
        order.verify(messages).renderFor(player, "start-sequence.started");
        order.verify(messages).renderFor(player, "start-sequence.started-subtitle");
    }

    @Test
    void trigger_whenAlreadyStarted_throwsIllegalState() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        assertThatThrownBy(() -> svc.trigger(world)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void trigger_whenCountdownCompletes_delegatesExactlyOneBorderTransition() {
        DefaultStartSequenceService svc = createService();

        svc.trigger(world);
        scheduler.advance(Duration.ofSeconds(3));

        assertThat(startedMoves()).hasSize(1);
        WorldBorderBoundsChangeEvent transition = startedMoves().get(0);
        assertThat(transition.getNewSize()).isEqualTo(30_000_000.0);
        // 30,000 seconds × 20 ticks/second, converted only at the Bukkit boundary.
        assertThat(transition.getDurationTicks()).isEqualTo(600_000L);

        // No hidden second phase or final snap: one continuous transition, ever.
        scheduler.advance(Duration.ofSeconds(120_000));
        assertThat(startedMoves()).hasSize(1);
    }

    @Test
    void trigger_convertsConfiguredDurationToTicksOnlyAtTheBoundary() {
        config = new StartSequenceConfig(
                Duration.ZERO, 50.0, 30_000_000.0, Duration.ofSeconds(90));
        DefaultStartSequenceService svc = createService();

        svc.trigger(world);
        scheduler.advance(Duration.ZERO);

        assertThat(startedMoves()).hasSize(1);
        assertThat(startedMoves().get(0).getDurationTicks()).isEqualTo(1_800L);
        assertThat(startedMoves().get(0).getNewSize()).isEqualTo(30_000_000.0);
    }

    @Test
    void trigger_afterRestart_persistedFlag_doesNotReissueTransition() throws Exception {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(startedMoves()).hasSize(1);

        DefaultStartSequenceService restarted = new DefaultStartSequenceService(
                scheduler, config, messages, platform, reopenConfig());

        assertThat(restarted.hasStarted()).isTrue();
        assertThatThrownBy(() -> restarted.trigger(world)).isInstanceOf(IllegalStateException.class);
        assertThat(startedMoves()).hasSize(1);
    }

    @Test
    void reset_whenRunning_cancelsPendingBorderExpansion() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        svc.reset();

        // Advance well past the countdown — no delegated transition should fire.
        scheduler.advance(Duration.ofSeconds(60));
        assertThat(startedMoves()).isEmpty();
    }

    @Test
    void reset_resetsBorderToInitialSize() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        svc.reset();

        assertThat(world.getWorldBorder().getSize()).isEqualTo(50.0);
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

        DefaultStartSequenceService restarted = new DefaultStartSequenceService(
                scheduler, config, messages, platform, reopenConfig());

        assertThat(restarted.hasStarted()).isFalse();
    }

    @Test
    void trigger_afterReset_succeeds() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);
        svc.reset();

        svc.trigger(world);
        assertThat(svc.hasStarted()).isTrue();
    }

    @Test
    void trigger_persistsStartedStateAcrossRestart() throws Exception {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        DefaultStartSequenceService restarted = new DefaultStartSequenceService(
                scheduler, config, messages, platform, reopenConfig());
        assertThat(restarted.hasStarted()).isTrue();
        assertThatThrownBy(() -> restarted.trigger(world)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void trigger_writesStartedToServerConfiguration_andNoRetiredDataFile() throws Exception {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        assertThat(
                org.spongepowered.configurate.yaml.YamlConfigurationLoader.builder()
                        .path(tempDir.resolve("config.yml"))
                        .build()
                        .load()
                        .node("modules", "start-sequence", "has-started")
                        .getBoolean())
                .isTrue();
        assertThat(java.nio.file.Files.exists(tempDir.resolve("data/start-sequence.yml")))
                .isFalse();
    }

    @Test
    void shutdown_cancelsPendingTasks() {
        DefaultStartSequenceService svc = createService();
        svc.trigger(world);

        svc.shutdown();

        scheduler.advance(Duration.ofSeconds(60));
        assertThat(startedMoves()).isEmpty();
    }
}
