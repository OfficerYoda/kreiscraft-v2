package de.kreiscraft.shared.user;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.platform.PaperPlatform;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class UserListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private UserService users;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        users = new UserService(new PaperPlatform(plugin, server), Clock.systemUTC());
        server.getPluginManager().registerEvents(new UserListener(users), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void join_registersUser() {
        PlayerMock alice = server.addPlayer("Alice"); // fires PlayerJoinEvent
        assertThat(users.get(alice.getUniqueId())).isPresent();
    }

    @Test
    void quit_removesUser() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.disconnect();
        assertThat(users.get(alice.getUniqueId())).isEmpty();
    }
}
