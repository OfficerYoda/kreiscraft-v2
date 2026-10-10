package de.kreiscraft.modules.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.testsupport.CapturingCommandRegistrar;
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
    void enable_doesNotRegisterStylePreviewAfterPaletteApproval() {
        CapturingCommandRegistrar registrar = enableAdminModule();
        assertThat(registrar.names()).doesNotContain("commandstylepreview");
    }

    @Test
    void enable_registersExpectedProductionCommands() {
        CapturingCommandRegistrar registrar = enableAdminModule();
        assertThat(registrar.names()).contains("item", "skull", "ip", "uuid", "ping", "tps");
    }

    private CapturingCommandRegistrar enableAdminModule() {
        CapturingCommandRegistrar registrar = new CapturingCommandRegistrar();
        ServiceRegistry services = new ServiceRegistry();
        services.publish(
                PlaceholderResolver.class,
                new PlaceholderResolver(server, ZoneId.of("Europe/Berlin"), Clock.systemUTC()));
        new AdminModule()
                .enable(new ModuleContext(
                        plugin,
                        CommentedConfigurationNode.root(),
                        mock(MessageService.class),
                        services,
                        mock(Scheduler.class),
                        registrar,
                        mock(EventBus.class)));
        return registrar;
    }
}
