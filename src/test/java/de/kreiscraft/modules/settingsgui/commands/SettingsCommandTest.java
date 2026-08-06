package de.kreiscraft.modules.settingsgui.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.settingsgui.DefaultSettingsService;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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

@ExtendWith(MockitoExtension.class)
class SettingsCommandTest {

    @Mock
    private DefaultSettingsService service;
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
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(service, messages, gui);
        assertThat(node.getName()).isEqualTo("settings");
    }

    @Test
    void build_commandHasExecutor() {
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(service, messages, gui);
        assertThat(node.getCommand()).isNotNull();
    }

    @Test
    void permission_denied_whenPlayerLacksPermission() {
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(service, messages, gui);

        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.settingsgui.use", false);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);

        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void permission_granted_whenPlayerHasPermission() {
        LiteralCommandNode<CommandSourceStack> node = SettingsCommand.build(service, messages, gui);

        PlayerMock player = server.addPlayer("Bob");
        player.addAttachment(plugin, "kreiscraft.settingsgui.use", true);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);

        assertThat(node.getRequirement().test(src)).isTrue();
    }
}
