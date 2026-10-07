package de.kreiscraft.modules.settingsgui.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleEnablement;
import de.kreiscraft.modules.settingsgui.SettingsService;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class SettingsCommandTest {

    @Mock
    private SettingsService service;
    @Mock
    private ModuleEnablement modules;
    @Mock
    private MessageService messages;
    @Mock
    private Gui gui;

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
    void build_commandNamedSettings() {
        LiteralCommandNode<CommandSourceStack> node =
                SettingsCommand.build(service, modules, messages, gui);
        assertThat(node.getName()).isEqualTo("settings");
    }

    @Test
    void build_commandHasExecutor() {
        LiteralCommandNode<CommandSourceStack> node =
                SettingsCommand.build(service, modules, messages, gui);
        assertThat(node.getCommand()).isNotNull();
    }

    @Test
    void permission_denied_whenPlayerLacksPermission() {
        LiteralCommandNode<CommandSourceStack> node =
                SettingsCommand.build(service, modules, messages, gui);

        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.settingsgui.use", false);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);

        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void permission_granted_whenPlayerHasPermission() {
        LiteralCommandNode<CommandSourceStack> node =
                SettingsCommand.build(service, modules, messages, gui);

        PlayerMock player = server.addPlayer("Bob");
        player.addAttachment(plugin, "kreiscraft.settingsgui.use", true);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);

        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @Test
    void execute_player_opensRootGuiWithRenderedTitle() throws Exception {
        LiteralCommandNode<CommandSourceStack> node =
                SettingsCommand.build(service, modules, messages, gui);
        PlayerMock player = server.addPlayer("Carol");
        Component title = Component.text("Settings");
        when(messages.renderFor(player, "settings-gui.gui-title")).thenReturn(title);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(source);

        int result = node.getCommand().run(ctx);

        assertThat(result).isEqualTo(Command.SINGLE_SUCCESS);
        verify(gui)
                .open(eq(player), any(xyz.xenondevs.invui.gui.Gui.class), eq(title));
    }

    @Test
    void execute_nonPlayer_returnsSuccessWithoutOpening() throws Exception {
        LiteralCommandNode<CommandSourceStack> node =
                SettingsCommand.build(service, modules, messages, gui);
        CommandSender consoleSender = mock(CommandSender.class);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(consoleSender);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(source);

        int result = node.getCommand().run(ctx);

        assertThat(result).isEqualTo(Command.SINGLE_SUCCESS);
        verify(gui, never()).open(any(), any(xyz.xenondevs.invui.gui.Gui.class), any());
    }
}
