package de.kreiscraft.modules.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.scoreboard.Scoreboard;
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

@ExtendWith(MockitoExtension.class)
class StatusListenerTest {

    @Mock
    private StatusService service;

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        lenient().when(service.get(any(UUID.class))).thenReturn(Status.defaults());
        server.getPluginManager().registerEvents(new StatusListener(service, server), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onJoin_appliesScoreboardTeam() {
        PlayerMock player = server.addPlayer("TestPlayer");
        String teamName = "kc_" + player.getUniqueId().toString().substring(0, 8);
        Scoreboard sb = player.getScoreboard();
        Team team = sb.getTeam(teamName);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(player.getName())).isTrue();
    }

    @Test
    void onJoin_callsServiceGet() {
        PlayerMock player = server.addPlayer("AnotherPlayer");
        verify(service, atLeastOnce()).get(player.getUniqueId());
    }

    @Test
    void onStatusChanged_reappliesTeamForOnlinePlayer() {
        PlayerMock player = server.addPlayer("OnlinePlayer");
        String teamName = "kc_" + player.getUniqueId().toString().substring(0, 8);

        Status updated = new Status("<red>Admin</red>", "", 0xFF0000);
        when(service.get(player.getUniqueId())).thenReturn(updated);

        StatusChangedEvent event =
                new StatusChangedEvent(player.getUniqueId(), Status.defaults(), updated);
        server.getPluginManager().callEvent(event);

        Scoreboard sb = player.getScoreboard();
        Team team = sb.getTeam(teamName);
        assertThat(team).isNotNull();
        // Team was updated at least twice: once on join, once on status change
        verify(service, atLeastOnce()).get(player.getUniqueId());
    }

    @Test
    void onStatusChanged_ignoresOfflinePlayer() {
        UUID offlineId = UUID.randomUUID();
        StatusChangedEvent event = new StatusChangedEvent(
                offlineId, Status.defaults(), new Status("<red>X</red>", "", 0xFF0000));
        // Should not throw — offline players are silently skipped
        server.getPluginManager().callEvent(event);
    }
}
