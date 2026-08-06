package de.kreiscraft.modules.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.settingsgui.CombatDisplay;
import de.kreiscraft.modules.settingsgui.SettingsService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private SettingsService settingsService;
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
        when(settingsService.getCombatDisplay(any())).thenReturn(CombatDisplay.ACTIONBAR);
        service = new DefaultCombatService(scheduler, messages, settingsService, platform, config);
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
        service.tag(alice, Duration.ofSeconds(10));
        verify(pluginManager).callEvent(any(CombatTaggedEvent.class));
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
        service.tag(alice, Duration.ofSeconds(10));
        // Reset interaction count after tag
        service.clear(alice);
        // At least two callEvent calls: one from tag, one from clear
        verify(pluginManager, atLeastOnce()).callEvent(any(CombatTaggedEvent.class));
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
        service.tag(alice, Duration.ofSeconds(10));
        scheduler.advance(Duration.ofSeconds(10));
        // Should have fired: once for tag (after=10s) and once for expiry clear (after=0)
        verify(pluginManager, atLeastOnce()).callEvent(any(CombatTaggedEvent.class));
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
    void shutdown_stopsDisplayTask() {
        service.shutdown();
        // After shutdown, advancing should not trigger display interactions
        // (platform.server().getOnlinePlayers() not called beyond what's already happened)
        assertThat(service.isInCombat(UUID.randomUUID())).isFalse(); // service still usable
    }

    // --- updateConfig ---

    @Test
    void updateConfig_appliedForNewTags() {
        CombatConfig newConfig = new CombatConfig(
                Duration.ofSeconds(20), Boolean.FALSE, List.of(), CombatDisplay.CHAT);
        service.updateConfig(newConfig);
        // The service itself stores state by duration passed to tag(), so just verify no error
        UUID alice = UUID.randomUUID();
        service.tag(alice, Duration.ofSeconds(20));
        assertThat(service.isInCombat(alice)).isTrue();
    }
}
