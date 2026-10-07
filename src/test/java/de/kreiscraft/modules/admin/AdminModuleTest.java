package de.kreiscraft.modules.admin;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.gui.GuiRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
    void enable_registersGmRedirectAndTemporaryStylePreview() {
        CommandRegistrar commands = mock(CommandRegistrar.class);
        ServiceRegistry services = new ServiceRegistry();
        services.publish(
                PlaceholderResolver.class,
                new PlaceholderResolver(server, ZoneId.of("Europe/Berlin"), Clock.systemUTC()));
        services.publish(Gui.class, new Gui(new GuiRegistry()));
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
        verify(commands)
                .register(
                        argThat(
                                (LiteralCommandNode<CommandSourceStack> node) -> node.getName()
                                        .equals("commandstylepreview")),
                        anyString());
    }
}
