package de.kreiscraft.modules.settingsgui.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.modules.settingsgui.SettingsGui.Page;
import de.kreiscraft.modules.settingsgui.SettingsNavigator;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
    private SettingsNavigator navigator;

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
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(navigator);
        assertThat(node.getName()).isEqualTo("settings");
    }

    @Test
    void rootRequirement_requiresUsePermission() {
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(navigator);

        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.settingsgui.use", true);
        CommandSourceStack allowed = mock(CommandSourceStack.class);
        when(allowed.getSender()).thenReturn(player);

        PlayerMock denied = server.addPlayer("Bob");
        denied.addAttachment(plugin, "kreiscraft.settingsgui.use", false);
        CommandSourceStack blocked = mock(CommandSourceStack.class);
        when(blocked.getSender()).thenReturn(denied);

        assertThat(node.getRequirement().test(allowed)).isTrue();
        assertThat(node.getRequirement().test(blocked)).isFalse();
    }

    @Test
    void adminRequirement_requiresAdminPermission() {
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(navigator);
        CommandNode<CommandSourceStack> admin = node.getChild("admin");
        assertThat(admin).isNotNull();

        PlayerMock adminPlayer = server.addPlayer("Admin");
        adminPlayer.addAttachment(plugin, "kreiscraft.settingsgui.admin", true);
        CommandSourceStack allowed = mock(CommandSourceStack.class);
        when(allowed.getSender()).thenReturn(adminPlayer);

        PlayerMock regular = server.addPlayer("Regular");
        regular.addAttachment(plugin, "kreiscraft.settingsgui.admin", false);
        CommandSourceStack blocked = mock(CommandSourceStack.class);
        when(blocked.getSender()).thenReturn(regular);

        assertThat(admin.getRequirement().test(allowed)).isTrue();
        assertThat(admin.getRequirement().test(blocked)).isFalse();
    }

    @Test
    void execute_player_opensRootGui() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(navigator);
        PlayerMock player = server.addPlayer("Carol");

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(source);

        int result = node.getCommand().run(ctx);

        assertThat(result).isEqualTo(Command.SINGLE_SUCCESS);
        verify(navigator).open(player, Page.ROOT);
    }

    @Test
    void execute_admin_opensServerConfiguration() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(navigator);
        CommandNode<CommandSourceStack> admin = node.getChild("admin");
        PlayerMock player = server.addPlayer("Admin");

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(source);

        int result = admin.getCommand().run(ctx);

        assertThat(result).isEqualTo(Command.SINGLE_SUCCESS);
        verify(navigator).openAdmin(player);
    }

    @Test
    void execute_nonPlayer_returnsSuccessWithoutOpening() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(navigator);
        CommandSender consoleSender = mock(CommandSender.class);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(consoleSender);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(source);

        int result = node.getCommand().run(ctx);

        assertThat(result).isEqualTo(Command.SINGLE_SUCCESS);
        verify(navigator, never()).open(any(), any());
    }
}
