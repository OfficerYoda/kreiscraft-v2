package de.kreiscraft.modules.tablist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.modules.status.Status;
import de.kreiscraft.modules.status.StatusListener;
import de.kreiscraft.modules.status.StatusService;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Guards acceptance criterion 5: the status module's scoreboard prefix, suffix and color must stay
 * visible after the tab-list module refreshes a player on join.
 */
@ExtendWith(MockitoExtension.class)
class TablistStatusCoexistenceTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int GOLD = 0xFFAA00;

    @Mock
    private StatusService statusService;

    @Mock
    private PlaceholderResolver resolver;

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        when(resolver.resolveString(any(), any())).thenReturn("resolved");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tablistJoinRefresh_doesNotDisturbStatusScoreboardTeam() {
        Status status = new Status("<gold>Admin</gold>", " <gray>star", GOLD);
        when(statusService.get(any(UUID.class))).thenReturn(status);

        TablistUpdater updater = new TablistUpdater(server, resolver, "header", "footer");
        server.getPluginManager().registerEvents(new StatusListener(statusService, server), plugin);
        server.getPluginManager().registerEvents(new TablistListener(updater), plugin);

        PlayerMock alice = server.addPlayer("Alice");

        Team team =
                alice.getScoreboard().getTeam("kc_" + alice.getUniqueId().toString().substring(0, 8));
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(alice.getName())).isTrue();
        assertThat(team.prefix()).isEqualTo(MM.deserialize(status.prefix()));
        assertThat(team.suffix()).isEqualTo(MM.deserialize(status.suffix()));
        assertThat(team.color()).isEqualTo(NamedTextColor.GOLD);
    }
}
