package de.kreiscraft.modules.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Scheduler-facing coverage for the combat countdown: a fresh tag must render its full duration
 * immediately and then step down exactly once per second until it expires.
 *
 * <p>The display is observed at the {@link Player} action-bar seam under virtual
 * {@link MockScheduler} time, so the sequence is asserted against the configured tag duration rather
 * than wall-clock drift.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CombatCountdownTest {

    @Mock
    private MessageService messages;
    @Mock
    private PlayerPreferencesService preferences;
    @Mock
    private PaperPlatform platform;
    @Mock
    private Server server;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private Player player;

    private MockScheduler scheduler;
    private DefaultCombatService service;
    private final UUID alice = UUID.randomUUID();
    private final List<String> actionBars = new ArrayList<>();

    @BeforeEach
    void setUp() {
        scheduler = new MockScheduler();
        CombatConfig config = new CombatConfig(
                Duration.ofSeconds(5), Boolean.TRUE, List.of(), CombatDisplay.ACTIONBAR);
        when(platform.server()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getPlayer(alice)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(preferences.combatDisplay(alice, CombatDisplay.ACTIONBAR))
                .thenReturn(CombatDisplay.ACTIONBAR);
        // Render the seconds placeholder straight into the component so the test reads the ticked
        // value through the public display seam.
        when(messages.renderFor(eq(player), eq("combat.remaining"), any(), any()))
                .thenAnswer(
                        inv -> {
                            Object seconds = inv.getArgument(3);
                            return Component.text(String.valueOf(seconds));
                        });
        doAnswer(
                inv -> {
                    actionBars.add(
                            PlainTextComponentSerializer.plainText()
                                    .serialize(inv.getArgument(0)));
                    return null;
                })
                        .when(player)
                        .sendActionBar(any(Component.class));
        service = new DefaultCombatService(scheduler, messages, preferences, platform, config);
    }

    @Test
    void fiveSecondTag_displaysFiveImmediatelyThenCountsDownToZero() {
        service.tag(alice, Duration.ofSeconds(5));

        assertThat(shownSeconds())
                .as("the full duration must show before the first tick")
                .containsExactly("5");

        scheduler.advance(Duration.ofSeconds(1));
        assertThat(shownSeconds()).containsExactly("5", "4");
        assertThat(service.isInCombat(alice)).isTrue();

        scheduler.advance(Duration.ofSeconds(1));
        assertThat(shownSeconds()).containsExactly("5", "4", "3");

        scheduler.advance(Duration.ofSeconds(1));
        assertThat(shownSeconds()).containsExactly("5", "4", "3", "2");

        scheduler.advance(Duration.ofSeconds(1));
        assertThat(shownSeconds()).containsExactly("5", "4", "3", "2", "1");
        assertThat(service.isInCombat(alice)).isTrue();

        scheduler.advance(Duration.ofSeconds(1));
        assertThat(shownSeconds()).containsExactly("5", "4", "3", "2", "1");
        assertThat(service.isInCombat(alice)).as("the tag must expire after five seconds").isFalse();
    }

    @Test
    void retag_resetsCountdownToFullDurationImmediately() {
        service.tag(alice, Duration.ofSeconds(5));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(shownSeconds()).containsExactly("5", "4", "3", "2");

        service.tag(alice, Duration.ofSeconds(5));

        assertThat(shownSeconds()).containsExactly("5", "4", "3", "2", "5");
        assertThat(service.remaining(alice)).isEqualTo(Duration.ofSeconds(5));
    }

    /** Every action-bar component shown so far, in order, as plain text. */
    private List<String> shownSeconds() {
        return List.copyOf(actionBars);
    }
}
