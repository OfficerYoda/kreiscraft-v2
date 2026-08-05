package de.kreiscraft.core.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.bukkit.entity.Player;
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
}
