package de.kreiscraft.modules.invsee.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.OfflineInventoryReader;
import de.kreiscraft.platform.SlottedItem;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.util.List;
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
class InvseeCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private Gui gui;
    private MessageService messages;
    private OfflineInventoryReader offlineReader;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        gui = mock(Gui.class);
        messages = mock(MessageService.class);
        offlineReader = mock(OfflineInventoryReader.class);
        when(messages.render(any(), any(Object[].class))).thenReturn(Component.empty());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_commandNameIsInvsee() {
        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader);
        assertThat(node.getLiteral()).isEqualTo("invsee");
    }

    @Test
    void build_requiresViewPermission_deniedWithoutPerm() {
        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        Player viewer = server.addPlayer();
        when(mockSrc.getSender()).thenReturn(viewer);
        // Non-op player with no permissions → denied
        assertThat(node.getRequirement().test(mockSrc)).isFalse();
    }

    @Test
    void build_requiresViewPermission_allowedWithPerm() {
        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        PlayerMock viewer = server.addPlayer();
        viewer.addAttachment(plugin, "kreiscraft.invsee.view", true);
        when(mockSrc.getSender()).thenReturn(viewer);
        assertThat(node.getRequirement().test(mockSrc)).isTrue();
    }

    @Test
    void build_hasTargetArgument() {
        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader);
        assertThat(node.getChildren()).isNotEmpty();
        assertThat(node.getChildren().iterator().next().getName()).isEqualTo("target");
    }

    @Test
    void build_withEditPerm_nodeStructureValid() {
        PlayerMock viewer = server.addPlayer("editor");
        viewer.addAttachment(plugin, "kreiscraft.invsee.view", true);
        viewer.addAttachment(plugin, "kreiscraft.invsee.edit", true);
        server.addPlayer("target");
        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader);
        assertThat(node.getLiteral()).isEqualTo("invsee");
        assertThat(node.getChildren()).isNotEmpty();
    }

    @Test
    void build_offlineReaderInjected_nodeStructureValid() throws IOException {
        when(offlineReader.readInventory(any()))
                .thenReturn(
                        List.of(
                                new SlottedItem(
                                        9,
                                        new org.bukkit.inventory.ItemStack(
                                                org.bukkit.Material.DIRT))));
        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader);
        assertThat(node.getLiteral()).isEqualTo("invsee");
    }
}
