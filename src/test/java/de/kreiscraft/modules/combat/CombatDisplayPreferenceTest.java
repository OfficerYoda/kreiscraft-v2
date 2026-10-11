package de.kreiscraft.modules.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import de.kreiscraft.testsupport.MockScheduler;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
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
 * Verifies that combat display is driven by the always-available preference, falling back to the
 * combat module's configured default when the player has no stored preference.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CombatDisplayPreferenceTest {

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

    private final RecordingMessages messages = new RecordingMessages();
    private MockScheduler scheduler;
    private UUID alice;

    @BeforeEach
    void setUp() {
        scheduler = new MockScheduler();
        alice = UUID.randomUUID();
        when(platform.server()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getPlayer(alice)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
    }

    private DefaultCombatService serviceWithDefault(CombatDisplay moduleDefault) {
        CombatConfig config =
                new CombatConfig(Duration.ofSeconds(60), Boolean.TRUE, List.of("Lobby"), moduleDefault);
        DefaultCombatService service =
                new DefaultCombatService(scheduler, messages, preferences, platform, () -> config);
        service.tag(alice, Duration.ofSeconds(60));
        scheduler.advance(Duration.ofSeconds(1));
        return service;
    }

    @Test
    void storedPreference_overridesModuleDefault() {
        when(preferences.combatDisplay(any(), any())).thenReturn(CombatDisplay.CHAT);

        serviceWithDefault(CombatDisplay.TITLE);

        assertThat(messages.sentKeys).contains("combat.remaining");
        verify(player, never()).sendActionBar(any(Component.class));
        verify(player, never()).showTitle(any(Title.class));
    }

    @Test
    void absentPreference_usesModuleDefaultTitle() {
        when(preferences.combatDisplay(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        serviceWithDefault(CombatDisplay.TITLE);

        verify(player, atLeastOnce()).showTitle(any(Title.class));
        assertThat(messages.sentKeys).isEmpty();
    }

    @Test
    void absentPreference_usesModuleDefaultActionbar() {
        when(preferences.combatDisplay(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        serviceWithDefault(CombatDisplay.ACTIONBAR);

        verify(player, atLeastOnce()).sendActionBar(any(Component.class));
        assertThat(messages.sentKeys).isEmpty();
    }

    /** Minimal {@link MessageService} that records which keys were sent, without MiniMessage. */
    private static final class RecordingMessages implements MessageService {

        private final List<String> sentKeys = new ArrayList<>();

        @Override
        public Component render(String key, Object... placeholders) {
            return Component.text(key);
        }

        @Override
        public void send(CommandSender sender, String key, Object... placeholders) {
            sentKeys.add(key);
        }

        @Override
        public void broadcast(String key, Object... placeholders) {
            sentKeys.add(key);
        }

        @Override
        public void reload() throws IOException {
            // no-op
        }
    }
}
