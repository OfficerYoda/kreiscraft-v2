package de.kreiscraft.core.module;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.kreiscraft.core.message.MessageService;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class ModuleFailureNotificationListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = mock(MessageService.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onJoin_notifiesOperatorAboutFailedModules() {
        server.getPluginManager().registerEvents(
                new ModuleFailureNotificationListener(List.of("api", "bedrock"), messages), plugin);
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);

        server.getPluginManager().callEvent(new PlayerJoinEvent(operator, Component.empty()));

        verify(messages).send(operator, "core.startup.failed-modules", "modules", "api, bedrock");
    }

    @Test
    void onJoin_doesNotNotifyNonOperator() {
        server.getPluginManager().registerEvents(
                new ModuleFailureNotificationListener(List.of("api"), messages), plugin);

        server.addPlayer("Player");

        verifyNoInteractions(messages);
    }

    @Test
    void onJoin_doesNothingWhenNoModulesFailed() {
        server.getPluginManager().registerEvents(
                new ModuleFailureNotificationListener(List.of(), messages), plugin);
        PlayerMock operator = server.addPlayer("Operator");
        operator.setOp(true);

        server.getPluginManager().callEvent(new PlayerJoinEvent(operator, Component.empty()));

        verifyNoInteractions(messages);
    }
}
