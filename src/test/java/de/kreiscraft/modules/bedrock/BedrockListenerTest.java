package de.kreiscraft.modules.bedrock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import de.kreiscraft.testsupport.MockScheduler;
import java.net.InetAddress;
import java.util.UUID;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class BedrockListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private BedrockService service;
    private MockScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        service = mock(BedrockService.class);
        scheduler = new MockScheduler();
        server.getPluginManager().registerEvents(
                new BedrockListener(service, scheduler), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Fires an async event from a background thread (MockBukkit enforces this). */
    private void fireAsync(AsyncPlayerPreLoginEvent event) throws Exception {
        Thread t = new Thread(() -> server.getPluginManager().callEvent(event));
        t.start();
        t.join();
    }

    @Test
    void preLogin_withBedrockPrefix_callsHandleLogin() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.isBedrock(".steve")).thenReturn(true);

        // AsyncPlayerPreLoginEvent must be fired off the main thread
        fireAsync(new AsyncPlayerPreLoginEvent(
                ".steve", InetAddress.getLoopbackAddress(), uuid));

        // MockScheduler.runOnMain() executes the runnable synchronously on the firing thread
        verify(service).handleLogin(".steve", uuid);
    }

    @Test
    void preLogin_withoutBedrockPrefix_doesNotHandleLogin() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.isBedrock("Alice")).thenReturn(false);

        fireAsync(new AsyncPlayerPreLoginEvent(
                "Alice", InetAddress.getLoopbackAddress(), uuid));

        verify(service, never()).handleLogin(any(), any());
    }
}
