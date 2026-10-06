package de.kreiscraft.core.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaceholderResolverTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesPlayerName() {
        PlayerMock alice = server.addPlayer("Alice");
        PlaceholderResolver r = new PlaceholderResolver(server, ZoneId.of("Europe/Berlin"),
                Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("Europe/Berlin")));
        assertThat(r.resolveString("hi %player_name%", alice)).isEqualTo("hi Alice");
    }

    @Test
    void resolvesServerPlayers() {
        server.addPlayer("A");
        server.addPlayer("B");
        PlaceholderResolver r = new PlaceholderResolver(server, ZoneId.of("UTC"), Clock.systemUTC());
        assertThat(r.resolveString("%server_players%", null)).isEqualTo("2");
    }

    @Test
    void leavesUnknownPlaceholderUntouched() {
        PlaceholderResolver r = new PlaceholderResolver(server, ZoneId.of("UTC"), Clock.systemUTC());
        assertThat(r.resolveString("hello %unknown%", null)).isEqualTo("hello %unknown%");
    }

    @Test
    void resolvesSystemTimeWithSeconds() {
        PlaceholderResolver r = new PlaceholderResolver(server, ZoneId.of("Europe/Berlin"),
                Clock.fixed(Instant.parse("2026-01-01T11:34:56Z"), ZoneId.of("Europe/Berlin")));
        assertThat(r.resolveString("%system_time%", null)).isEqualTo("12:34:56");
    }

    @Test
    void resolvesPlaytimeAsHoursMinutesSeconds() {
        PlayerMock alice = server.addPlayer("Alice");
        int ticks = (int) (((3L * 3600L) + (4L * 60L) + 5L) * 20L);
        alice.setStatistic(Statistic.PLAY_ONE_MINUTE, ticks);
        PlaceholderResolver r = new PlaceholderResolver(server, ZoneId.of("UTC"), Clock.systemUTC());
        assertThat(r.resolveString("%playtime%", alice)).isEqualTo("03:04:05");
    }

    @Test
    void resolvesPlaytimeZeroPadded() {
        PlayerMock alice = server.addPlayer("Alice");
        PlaceholderResolver r = new PlaceholderResolver(server, ZoneId.of("UTC"), Clock.systemUTC());
        assertThat(r.resolveString("%playtime%", alice)).isEqualTo("00:00:00");
    }

    @Test
    void resolvesTpsCappedAtTwenty() {
        Server mockServer = mock(Server.class);
        when(mockServer.getTPS()).thenReturn(new double[] {21.5});
        PlaceholderResolver r =
                new PlaceholderResolver(mockServer, ZoneId.of("UTC"), Clock.systemUTC());
        assertThat(r.resolveString("%tps%", null)).isEqualTo("20.0");
    }

    @Test
    void resolvesTpsRoundedToAtMostTwoDecimals() {
        Server mockServer = mock(Server.class);
        when(mockServer.getTPS()).thenReturn(new double[] {19.876});
        PlaceholderResolver r =
                new PlaceholderResolver(mockServer, ZoneId.of("UTC"), Clock.systemUTC());
        assertThat(r.resolveString("%tps%", null)).isEqualTo("19.88");
    }
}
