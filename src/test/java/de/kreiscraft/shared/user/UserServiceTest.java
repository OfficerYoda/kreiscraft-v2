package de.kreiscraft.shared.user;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.platform.PaperPlatform;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class UserServiceTest {

    private ServerMock server;
    private PluginMock plugin;
    private PaperPlatform platform;
    private Clock clock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        platform = new PaperPlatform(plugin, server);
        clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void join_registersPlayer() {
        UserService svc = new UserService(platform, clock);
        PlayerMock alice = server.addPlayer("Alice");
        User user = svc.join(alice);
        assertThat(user.name()).isEqualTo("Alice");
        assertThat(svc.get(alice.getUniqueId())).contains(user);
        assertThat(svc.get(alice)).contains(user);
        assertThat(svc.all()).containsExactly(user);
    }

    @Test
    void join_isIdempotent() {
        UserService svc = new UserService(platform, clock);
        PlayerMock alice = server.addPlayer("Alice");
        User first = svc.join(alice);
        User second = svc.join(alice);
        assertThat(second).isSameAs(first);
        assertThat(svc.all()).containsExactly(first);
    }

    @Test
    void quit_removesAndReturnsUser() {
        UserService svc = new UserService(platform, clock);
        PlayerMock alice = server.addPlayer("Alice");
        User user = svc.join(alice);
        assertThat(svc.quit(alice.getUniqueId())).contains(user);
        assertThat(svc.get(alice.getUniqueId())).isEmpty();
        assertThat(svc.all()).isEmpty();
    }

    @Test
    void quit_unknownPlayer_returnsEmpty() {
        UserService svc = new UserService(platform, clock);
        assertThat(svc.quit(new PlayerMock(server, "Ghost").getUniqueId())).isEmpty();
    }

    @Test
    void rehydrateOnlinePlayers_addsEveryOnlinePlayer() {
        server.addPlayer("Alice");
        server.addPlayer("Bob");
        UserService svc = new UserService(platform, clock);
        svc.rehydrateOnlinePlayers();
        assertThat(svc.all())
                .hasSize(2)
                .extracting(User::name)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void joinedAt_usesInjectedClock() {
        UserService svc = new UserService(platform, clock);
        PlayerMock alice = server.addPlayer("Alice");
        User user = svc.join(alice);
        assertThat(user.joinedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
