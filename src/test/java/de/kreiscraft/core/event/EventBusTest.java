package de.kreiscraft.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventBusTest {

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void register_receivesEvent() {
        EventBus bus = new EventBus(plugin);
        AtomicInteger joins = new AtomicInteger();
        bus.register(new Listener() {
            @EventHandler
            public void on(PlayerJoinEvent e) {
                joins.incrementAndGet();
            }
        });

        server.addPlayer("alice");

        assertThat(joins).hasValue(1);
    }

    @Test
    void unregisterAll_stopsListening() {
        EventBus bus = new EventBus(plugin);
        AtomicInteger joins = new AtomicInteger();
        bus.register(new Listener() {
            @EventHandler
            public void on(PlayerJoinEvent e) {
                joins.incrementAndGet();
            }
        });

        bus.unregisterAll();
        server.addPlayer("alice");

        assertThat(joins).hasValue(0);
    }
}
