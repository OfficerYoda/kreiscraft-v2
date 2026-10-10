package de.kreiscraft.modules.spawn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.testsupport.MockScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.spongepowered.configurate.CommentedConfigurationNode;

class SpawnModuleTest {

    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void disabledGlideStillRegistersReloadableListener() throws Exception {
        CommentedConfigurationNode config = CommentedConfigurationNode.root();
        config.node("glide", "enabled").set(false);
        ServiceRegistry services = new ServiceRegistry();
        services.publish(ConfigService.class, mock(ConfigService.class));
        EventBus events = mock(EventBus.class);

        new SpawnModule().enable(new ModuleContext(
                plugin,
                config,
                mock(MessageService.class),
                services,
                new MockScheduler(),
                mock(CommandRegistrar.class),
                events));

        verify(events).register(any(GlideListener.class));
    }
}
