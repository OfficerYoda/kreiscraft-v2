package de.kreiscraft.modules.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultCombatServiceTest {

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

    private MockScheduler scheduler;
    private CombatConfig config;
    private DefaultCombatService service;

    @BeforeEach
    void setUp() {
        scheduler = new MockScheduler();
        config = new CombatConfig(Duration.ofSeconds(10), Boolean.TRUE, List.of("Lobby"), CombatDisplay.ACTIONBAR);
        when(platform.server()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(preferences.combatDisplay(any(), any())).thenReturn(CombatDisplay.ACTIONBAR);
        service = new DefaultCombatService(scheduler, messages, preferences, platform, () -> config);
    }

    // --- isInCombat ---

    @Test
    void isInCombat_whenNotTagged_returnsFalse() {
        assertThat(service.isInCombat(UUID.randomUUID())).isFalse();
    }

    @Test
    void isInCombat_afterTag_returnsTrue() {
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(10));
        assertThat(service.isInCombat(alice)).isTrue();
    }

    // --- remaining ---

    @Test
    void remaining_whenNotTagged_returnsZero() {
        assertThat(service.remaining(UUID.randomUUID())).isEqualTo(Duration.ZERO);
    }

    // --- tag ---

    @Test
    void tag_schedulesExpiryTask() {
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(5));
        // Advance past expiry — state should be cleared
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(service.isInCombat(alice)).isFalse();
    }

    @Test
    void tag_firesTaggedEvent_withZeroBefore_whenNotPreviouslyTagged() {
        UUID alice = UUID.randomUUID();
        ArgumentCaptor<CombatTaggedEvent> captor = ArgumentCaptor.forClass(CombatTaggedEvent.class);

        service.tag(alice, Duration.ofSeconds(10));

        verify(pluginManager, times(1)).callEvent(captor.capture());
        CombatTaggedEvent event = captor.getValue();
        assertThat(event.getUuid()).isEqualTo(alice);
        assertThat(event.getBefore()).isEqualTo(Duration.ZERO);
        assertThat(event.getAfter()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void tag_retag_cancelsPriorExpiry() {
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(5));
        // Re-tag with longer duration before the first one expires
        service.tag(alice, Duration.ofSeconds(20));
        // Advance 10s — old expiry at 5s should have been cancelled
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(service.isInCombat(alice)).isTrue();
    }

    @Test
    void tag_retag_expiresAtNewDuration() {
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(5));
        // Re-tag
        service.tag(alice, Duration.ofSeconds(20));
        // Advance past new expiry
        scheduler.advance(Duration.ofSeconds(20));
        assertThat(service.isInCombat(alice)).isFalse();
    }

    // --- clear ---

    @Test
    void clear_removesFromCombat() {
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(10));
        service.clear(alice);
        assertThat(service.isInCombat(alice)).isFalse();
    }

    @Test
    void clear_firesTaggedEventWithZeroAfter() {
        UUID alice = UUID.randomUUID();
        ArgumentCaptor<CombatTaggedEvent> captor = ArgumentCaptor.forClass(CombatTaggedEvent.class);

        service.tag(alice, Duration.ofSeconds(10));
        service.clear(alice);

        // Two callEvent invocations: tag (after=10s) + clear (after=ZERO)
        verify(pluginManager, times(2)).callEvent(captor.capture());
        List<CombatTaggedEvent> events = captor.getAllValues();
        assertThat(events.get(0).getAfter()).isEqualTo(Duration.ofSeconds(10));
        assertThat(events.get(1).getUuid()).isEqualTo(alice);
        assertThat(events.get(1).getAfter()).isEqualTo(Duration.ZERO);
    }

    @Test
    void clear_whenNotTagged_doesNothing() {
        UUID alice = UUID.randomUUID();
        // Should not throw
        service.clear(alice);
        assertThat(service.isInCombat(alice)).isFalse();
    }

    // --- advance clears via expiry ---

    @Test
    void advance_pastExpiry_clearsState() {
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(10));
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(service.isInCombat(alice)).isFalse();
    }

    @Test
    void advance_pastExpiry_firesTaggedEvent_withZeroAfter() {
        UUID alice = UUID.randomUUID();
        ArgumentCaptor<CombatTaggedEvent> captor = ArgumentCaptor.forClass(CombatTaggedEvent.class);

        service.tag(alice, Duration.ofSeconds(10));
        scheduler.advance(Duration.ofSeconds(10));

        // tag fires (after=10s), expiry-clear fires (after=ZERO)
        verify(pluginManager, times(2)).callEvent(captor.capture());
        List<CombatTaggedEvent> events = captor.getAllValues();
        assertThat(events.get(1).getUuid()).isEqualTo(alice);
        assertThat(events.get(1).getAfter()).isEqualTo(Duration.ZERO);
    }

    @Test
    void advance_beforeExpiry_stateRemains() {
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(10));
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(service.isInCombat(alice)).isTrue();
    }

    // --- independent players ---

    @Test
    void tag_differentPlayers_areIndependent() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(10));
        service.tag(bob, Duration.ofSeconds(5));

        scheduler.advance(Duration.ofSeconds(5));
        assertThat(service.isInCombat(alice)).isTrue();
        assertThat(service.isInCombat(bob)).isFalse();
    }

    // --- shutdown ---

    @Test
    void shutdown_stopsDisplayTask_andClearsState() {
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(10));
        service.shutdown();

        // State is cleared
        assertThat(service.isInCombat(alice)).isFalse();
        // Advancing should not trigger expiry callEvent (expiry task cancelled by shutdown)
        verify(pluginManager, atLeastOnce()).callEvent(any(CombatTaggedEvent.class)); // from tag()
    }

    // --- config swap ---

    @Test
    void configSwap_appliedForNewTags() {
        CombatConfig newConfig = new CombatConfig(
                Duration.ofSeconds(20), Boolean.FALSE, List.of(), CombatDisplay.CHAT);
        config = newConfig;
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(20));
        assertThat(service.isInCombat(alice)).isTrue();
    }
}
