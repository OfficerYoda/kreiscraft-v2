package de.kreiscraft.modules.glow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class GlowListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private DefaultGlowService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        service = new DefaultGlowService(server);
        server.getPluginManager().registerEvents(new GlowListener(service), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void quit_clearsGlowStateFromMap() {
        PlayerMock alice = server.addPlayer("Alice");
        service.toggle(alice.getUniqueId());
        assertThat(service.isGlowing(alice.getUniqueId())).isTrue();

        alice.disconnect();

        assertThat(service.isGlowing(alice.getUniqueId())).isFalse();
    }

    @Test
    void quit_whenNotGlowing_remainsFalse() {
        PlayerMock alice = server.addPlayer("Alice");

        alice.disconnect();

        assertThat(service.isGlowing(alice.getUniqueId())).isFalse();
    }
}
