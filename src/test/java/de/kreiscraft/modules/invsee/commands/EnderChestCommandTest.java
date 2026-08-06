package de.kreiscraft.modules.invsee.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("UnstableApiUsage")
class EnderChestCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private Gui gui;
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        gui = mock(Gui.class);
        messages = mock(MessageService.class);
        when(messages.render(any(), any(Object[].class))).thenReturn(Component.empty());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_commandNameIsEnderchest() {
        LiteralCommandNode<CommandSourceStack> node = EnderChestCommand.build(gui, messages);
        assertThat(node.getLiteral()).isEqualTo("enderchest");
    }

    @Test
    void build_requiresViewPermission_deniedWithoutPerm() {
        LiteralCommandNode<CommandSourceStack> node = EnderChestCommand.build(gui, messages);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        Player viewer = server.addPlayer();
        when(mockSrc.getSender()).thenReturn(viewer);
        // Non-op, no permissions → denied
        assertThat(node.getRequirement().test(mockSrc)).isFalse();
    }

    @Test
    void build_requiresViewPermission_allowedWithPerm() {
        LiteralCommandNode<CommandSourceStack> node = EnderChestCommand.build(gui, messages);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        PlayerMock viewer = server.addPlayer();
        viewer.addAttachment(plugin, "kreiscraft.invsee.view", true);
        when(mockSrc.getSender()).thenReturn(viewer);
        assertThat(node.getRequirement().test(mockSrc)).isTrue();
    }

    @Test
    void build_hasTargetArgument() {
        LiteralCommandNode<CommandSourceStack> node = EnderChestCommand.build(gui, messages);
        assertThat(node.getChildren()).isNotEmpty();
        assertThat(node.getChildren().iterator().next().getName()).isEqualTo("target");
    }

    @Test
    void build_withEditPerm_nodeStructureValid() {
        PlayerMock viewer = server.addPlayer("editor");
        viewer.addAttachment(plugin, "kreiscraft.invsee.view", true);
        viewer.addAttachment(plugin, "kreiscraft.invsee.edit", true);
        server.addPlayer("target");
        LiteralCommandNode<CommandSourceStack> node = EnderChestCommand.build(gui, messages);
        assertThat(node.getLiteral()).isEqualTo("enderchest");
        assertThat(node.getChildren()).isNotEmpty();
    }

    @Test
    void build_offlineTarget_nodeStructureValid() {
        // No online player named "ghost" → expected to send not-found message
        LiteralCommandNode<CommandSourceStack> node = EnderChestCommand.build(gui, messages);
        assertThat(node.getChildren()).isNotEmpty();
    }
}
