package de.kreiscraft.modules.invsee.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionTestSupport;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.OfflinePlayerData;
import de.kreiscraft.platform.OfflinePlayerSnapshot;
import de.kreiscraft.platform.SlottedItem;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import xyz.xenondevs.invui.gui.SlotElement;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("UnstableApiUsage")
class EnderChestCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private Gui gui;
    private MessageService messages;
    private OfflinePlayerData offlineData;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        gui = mock(Gui.class);
        messages = mock(MessageService.class);
        offlineData = mock(OfflinePlayerData.class);
        when(messages.render(any(), any(Object[].class))).thenReturn(Component.empty());
        when(messages.renderFor(any(org.bukkit.command.CommandSender.class), any(String.class),
                any(Object[].class))).thenReturn(Component.text("title"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_commandNameIsEnderchest() {
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, offlineData, server);
        assertThat(node.getLiteral()).isEqualTo("enderchest");
    }

    @Test
    void build_requiresInvseeUsePermission_deniedWithoutPerm() {
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, offlineData, server);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        Player viewer = server.addPlayer();
        when(mockSrc.getSender()).thenReturn(viewer);
        assertThat(node.getRequirement().test(mockSrc)).isFalse();
    }

    @Test
    void build_requiresInvseeUsePermission_allowedWithPerm() {
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, offlineData, server);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        PlayerMock viewer = server.addPlayer();
        viewer.addAttachment(plugin, "kreiscraft.invsee.use", true);
        when(mockSrc.getSender()).thenReturn(viewer);
        assertThat(node.getRequirement().test(mockSrc)).isTrue();
    }

    @Test
    void build_hasTargetArgument() {
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, offlineData, server);
        assertThat(node.getChildren()).isNotEmpty();
        assertThat(node.getChildren().iterator().next().getName()).isEqualTo("target");
    }

    @Test
    void build_targetSuggestsOnlineAndKnownOfflineProfiles() {
        Server suggestionServer = mock(Server.class);
        Player online = mock(Player.class);
        when(online.getName()).thenReturn("Alice");
        when(suggestionServer.getOnlinePlayers()).thenAnswer(invocation -> List.of(online));
        OfflinePlayer known = mock(OfflinePlayer.class);
        when(known.hasPlayedBefore()).thenReturn(true);
        when(known.getName()).thenReturn("Carol");
        when(suggestionServer.getOfflinePlayers()).thenReturn(new OfflinePlayer[] {known});

        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, offlineData, suggestionServer);
        CommandSourceStack source = mock(CommandSourceStack.class);
        PlayerMock viewer = server.addPlayer();
        viewer.addAttachment(plugin, "kreiscraft.invsee.use", true);
        when(source.getSender()).thenReturn(viewer);

        CommandNode<CommandSourceStack> target = node.getChildren().iterator().next();
        List<String> suggestions = SuggestionTestSupport.suggestions(target, source, "");

        assertThat(suggestions).containsExactly("Alice", "Carol");
    }

    @Test
    void openEnderChest_opensEditableGuiWithAllEnderChestSlots() {
        PlayerMock viewer = server.addPlayer("viewer");
        PlayerMock target = server.addPlayer("target");

        EnderChestCommand.openEnderChest(viewer, target, gui, messages);

        ArgumentCaptor<xyz.xenondevs.invui.gui.Gui> captor =
                ArgumentCaptor.forClass(xyz.xenondevs.invui.gui.Gui.class);
        verify(gui).open(eq(viewer), captor.capture(), any(Component.class));
        long boundSlots =
                captor.getValue().getSize() == 27 ? countInventoryLinks(captor.getValue()) : -1;
        assertThat(boundSlots).isEqualTo(27);
    }

    @Test
    void openEnderChest_rendersLocalizedTitleForViewer() {
        PlayerMock viewer = server.addPlayer("viewer");
        PlayerMock target = server.addPlayer("target");

        EnderChestCommand.openEnderChest(viewer, target, gui, messages);

        verify(messages)
                .renderFor(eq(viewer), eq("invsee.gui-title-ec"), eq("player"), eq("target"));
    }

    @Test
    void build_withEditPerm_nodeStructureValid() {
        PlayerMock viewer = server.addPlayer("editor");
        viewer.addAttachment(plugin, "kreiscraft.invsee.use", true);
        server.addPlayer("target");
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, offlineData, server);
        assertThat(node.getLiteral()).isEqualTo("enderchest");
        assertThat(node.getChildren()).isNotEmpty();
    }

    @Test
    void build_offlineTarget_nodeStructureValid() {
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, offlineData, server);
        assertThat(node.getChildren()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // Execution: offline path
    // -----------------------------------------------------------------------

    @Test
    void openOfflineEnderChest_withItems_readsSnapshotAndOpensGui() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(offlineData.read(uuid))
                .thenReturn(
                        OfflinePlayerSnapshot.ofEnderItems(
                                List.of(new SlottedItem(0, new ItemStack(Material.SHULKER_BOX)))));

        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, offlineData);

        verify(offlineData).read(uuid);
        verify(gui).open(eq(viewer), any(xyz.xenondevs.invui.gui.Gui.class), any(Component.class));
        verify(messages)
                .renderFor(eq(viewer), eq("invsee.gui-title-ec"), eq("player"), eq("ghost"));
    }

    @Test
    void openOfflineEnderChest_neverPlayed_sendsNotFoundMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.hasPlayedBefore()).thenReturn(false);

        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, offlineData);

        verify(messages).send(viewer, "invsee.target-not-found", "player", "ghost");
        verify(offlineData, never()).read(any());
        verify(gui, never()).open(any(), any(), any());
    }

    @Test
    void openOfflineEnderChest_empty_sendsOfflineEmptyMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(offlineData.read(uuid)).thenReturn(OfflinePlayerSnapshot.empty());

        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, offlineData);

        verify(offlineData).read(uuid);
        verify(messages).send(viewer, "invsee.offline-empty-ec", "player", "ghost");
        verify(gui, never()).open(any(), any(), any());
    }

    @Test
    void openOfflineEnderChest_inventoryOnly_sendsOfflineEmptyMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(offlineData.read(uuid))
                .thenReturn(
                        OfflinePlayerSnapshot.ofInventory(
                                List.of(new SlottedItem(0, new ItemStack(Material.STONE)))));

        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, offlineData);

        verify(messages).send(viewer, "invsee.offline-empty-ec", "player", "ghost");
        verify(gui, never()).open(any(), any(), any());
    }

    @Test
    void openOfflineEnderChest_readError_sendsReadErrorMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(offlineData.read(uuid)).thenThrow(new IOException("disk error"));

        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, offlineData);

        verify(offlineData).read(uuid);
        verify(messages).send(viewer, "invsee.read-error");
        verify(gui, never()).open(any(), any(), any());
    }

    private static long countInventoryLinks(xyz.xenondevs.invui.gui.Gui invuiGui) {
        long count = 0;
        for (int i = 0; i < invuiGui.getSize(); i++) {
            if (invuiGui.getSlotElement(i) instanceof SlotElement.InventoryLink) {
                count++;
            }
        }
        return count;
    }
}
