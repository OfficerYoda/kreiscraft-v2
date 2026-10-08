package de.kreiscraft.modules.positions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.testsupport.CapturingCommandRegistrar;
import de.kreiscraft.testsupport.MockScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.spongepowered.configurate.CommentedConfigurationNode;

class PositionsModuleTest {

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
    void enableRegistersPosAlias() {
        ServiceRegistry services = new ServiceRegistry();
        services.publish(ConfigService.class, mock(ConfigService.class));
        services.publish(Gui.class, mock(Gui.class));
        CapturingCommandRegistrar commands = new CapturingCommandRegistrar();

        new PositionsModule().enable(new ModuleContext(
                plugin,
                CommentedConfigurationNode.root(),
                mock(MessageService.class),
                services,
                new MockScheduler(),
                commands,
                mock(EventBus.class)));

        assertThat(commands.names()).containsExactly("positions");
        assertThat(commands.aliases()).containsExactly("pos");
    }
}
