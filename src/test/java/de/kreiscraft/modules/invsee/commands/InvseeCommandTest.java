package de.kreiscraft.modules.invsee.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.OfflineInventoryReader;
import de.kreiscraft.platform.SlottedItem;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
        when(messages.render(any(), any(Object[].class))).thenReturn(Component.text("title"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -----------------------------------------------------------------------
    // Node-structure checks
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Execution: online path
    // -----------------------------------------------------------------------

    @Test
    void openOnlineInventory_withoutEditPerm_opensGuiForViewer() {
        PlayerMock viewer = server.addPlayer("viewer");
        PlayerMock target = server.addPlayer("target");

        InvseeCommand.openOnlineInventory(viewer, target, gui, messages);

        verify(gui).open(eq(viewer), any(xyz.xenondevs.invui.gui.Gui.class), any(Component.class));
    }

    @Test
    void openOnlineInventory_withEditPerm_opensGuiForViewer() {
        PlayerMock viewer = server.addPlayer("editor");
        viewer.addAttachment(plugin, "kreiscraft.invsee.edit", true);
        PlayerMock target = server.addPlayer("target2");

        InvseeCommand.openOnlineInventory(viewer, target, gui, messages);

        verify(gui).open(eq(viewer), any(xyz.xenondevs.invui.gui.Gui.class), any(Component.class));
    }

    // -----------------------------------------------------------------------
    // Execution: offline path
    // -----------------------------------------------------------------------

    @Test
    void openOfflineInventory_withItems_callsOfflineReaderAndOpensGui() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(offlineReader.readInventory(uuid))
                .thenReturn(List.of(new SlottedItem(9, new ItemStack(Material.DIRT))));

        InvseeCommand.openOfflineInventory(viewer, offline, "ghost", gui, messages, offlineReader);

        verify(offlineReader).readInventory(uuid);
        verify(gui).open(eq(viewer), any(xyz.xenondevs.invui.gui.Gui.class), any(Component.class));
    }

    @Test
    void openOfflineInventory_neverPlayed_sendsNotFoundMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.hasPlayedBefore()).thenReturn(false);

        InvseeCommand.openOfflineInventory(viewer, offline, "ghost", gui, messages, offlineReader);

        verify(messages).send(viewer, "invsee.target-not-found", "player", "ghost");
        verify(offlineReader, never()).readInventory(any());
        verify(gui, never()).open(any(), any(), any());
    }

    @Test
    void openOfflineInventory_emptyInventory_sendsOfflineEmptyMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(offlineReader.readInventory(uuid)).thenReturn(List.of());

        InvseeCommand.openOfflineInventory(viewer, offline, "ghost", gui, messages, offlineReader);

        verify(offlineReader).readInventory(uuid);
        verify(messages).send(viewer, "invsee.offline-empty", "player", "ghost");
        verify(gui, never()).open(any(), any(), any());
    }

    @Test
    void openOfflineInventory_readError_sendsReadErrorMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(offlineReader.readInventory(uuid)).thenThrow(new IOException("disk error"));

        InvseeCommand.openOfflineInventory(viewer, offline, "ghost", gui, messages, offlineReader);

        verify(offlineReader).readInventory(uuid);
        verify(messages).send(viewer, "invsee.read-error");
        verify(gui, never()).open(any(), any(), any());
    }
}
