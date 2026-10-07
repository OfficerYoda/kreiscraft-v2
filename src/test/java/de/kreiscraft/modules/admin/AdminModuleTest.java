package de.kreiscraft.modules.admin;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.core.service.ServiceRegistry;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.spongepowered.configurate.CommentedConfigurationNode;

class AdminModuleTest {

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
    void enable_registersGmAsRedirectToVanillaGamemode() {
        CommandRegistrar commands = mock(CommandRegistrar.class);
        ServiceRegistry services = new ServiceRegistry();
        services.publish(
                PlaceholderResolver.class,
                new PlaceholderResolver(server, ZoneId.of("Europe/Berlin"), Clock.systemUTC()));
        ModuleContext ctx = new ModuleContext(
                plugin,
                CommentedConfigurationNode.root(),
                mock(MessageService.class),
                services,
                mock(Scheduler.class),
                commands,
                mock(EventBus.class));

        new AdminModule().enable(ctx);

        verify(commands)
                .registerRedirect(eq("gm"), eq("gamemode"), anyString());
    }
}
