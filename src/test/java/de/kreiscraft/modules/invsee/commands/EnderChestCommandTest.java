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
import de.kreiscraft.platform.OfflineCommitResult;
import de.kreiscraft.platform.OfflinePlayerDataEditor;
import de.kreiscraft.platform.OfflinePlayerEditSession;
import de.kreiscraft.platform.OfflinePlayerSnapshot;
import de.kreiscraft.platform.SlottedItem;
import de.kreiscraft.platform.StagedOfflineWrite;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.testsupport.MockScheduler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
    private OfflinePlayerDataEditor editor;
    private OfflinePlayerEditSession session;
    private MockScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        gui = mock(Gui.class);
        messages = mock(MessageService.class);
        editor = mock(OfflinePlayerDataEditor.class);
        session = mock(OfflinePlayerEditSession.class);
        scheduler = new MockScheduler();
        when(messages.render(any(), any(Object[].class))).thenReturn(Component.empty());
        when(messages.renderFor(any(org.bukkit.command.CommandSender.class), any(String.class),
                any(Object[].class))).thenReturn(Component.text("title"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void commandEnderChestPolicyFollowsEggModuleForOnlineAndOfflineTargets(boolean enabled) throws Exception {
        var viewer = server.addPlayer("viewer");
        var target = server.addPlayer("target");
        EnderChestCommand.openEnderChest(viewer, target, gui, messages, () -> enabled);
        var live = ArgumentCaptor.forClass(xyz.xenondevs.invui.gui.Gui.class);
        verify(gui).open(eq(viewer), live.capture(), any(Component.class));
        var offline = offlineWithEnderItems(UUID.randomUUID(), new ItemStack(Material.DIAMOND));
        EnderChestCommand.openOfflineEnderChest(viewer, offline, "ghost", gui, messages, editor, scheduler,
                server, () -> enabled);
        var detached = ArgumentCaptor.forClass(xyz.xenondevs.invui.gui.Gui.class);
        verify(gui).open(eq(viewer), detached.capture(), any(Component.class), any());
        for (var contents : List.of(live.getValue(), detached.getValue())) {
            var link = (SlotElement.InventoryLink) contents.getSlotElement(0);
            assertThat(link.inventory().setItem(null, link.slot(), new ItemStack(Material.DRAGON_EGG)))
                    .isEqualTo(!enabled);
        }
    }

    @Test
    void build_commandNameIsEnderchest() {
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, editor, scheduler, server);
        assertThat(node.getLiteral()).isEqualTo("enderchest");
    }

    @Test
    void build_requiresInvseeUsePermission_deniedWithoutPerm() {
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, editor, scheduler, server);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        Player viewer = server.addPlayer();
        when(mockSrc.getSender()).thenReturn(viewer);
        assertThat(node.getRequirement().test(mockSrc)).isFalse();
    }

    @Test
    void build_requiresInvseeUsePermission_allowedWithPerm() {
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, editor, scheduler, server);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        PlayerMock viewer = server.addPlayer();
        viewer.addAttachment(plugin, "kreiscraft.invsee.use", true);
        when(mockSrc.getSender()).thenReturn(viewer);
        assertThat(node.getRequirement().test(mockSrc)).isTrue();
    }

    @Test
    void build_hasTargetArgument() {
        LiteralCommandNode<CommandSourceStack> node =
                EnderChestCommand.build(gui, messages, editor, scheduler, server);
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
                EnderChestCommand.build(gui, messages, editor, scheduler, suggestionServer);
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

    // -----------------------------------------------------------------------
    // Execution: offline open path
    // -----------------------------------------------------------------------

    @Test
    void openOfflineEnderChest_withItems_opensEditableSnapshotGui() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(editor.openForEdit(uuid)).thenReturn(Optional.of(session));
        when(session.snapshot())
                .thenReturn(
                        OfflinePlayerSnapshot.ofEnderItems(
                                List.of(new SlottedItem(0, new ItemStack(Material.SHULKER_BOX)))));

        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, editor, scheduler, server);

        verify(editor).openForEdit(uuid);
        verify(gui)
                .open(
                        eq(viewer),
                        any(xyz.xenondevs.invui.gui.Gui.class),
                        any(Component.class),
                        any());
        verify(messages)
                .renderFor(eq(viewer), eq("invsee.gui-title-ec"), eq("player"), eq("ghost"));
    }

    @Test
    void openOfflineEnderChest_neverPlayed_sendsNotFoundMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.hasPlayedBefore()).thenReturn(false);

        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, editor, scheduler, server);

        verify(messages).send(viewer, "invsee.target-not-found", "player", "ghost");
        verify(editor, never()).openForEdit(any());
        verify(gui, never()).open(any(), any(), any(), any());
    }

    @Test
    void openOfflineEnderChest_missingFile_sendsOfflineEmptyMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(editor.openForEdit(uuid)).thenReturn(Optional.empty());

        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, editor, scheduler, server);

        verify(messages).send(viewer, "invsee.offline-empty-ec", "player", "ghost");
        verify(gui, never()).open(any(), any(), any(), any());
    }

    @Test
    void openOfflineEnderChest_readError_sendsReadErrorMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        OfflinePlayer offline = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(editor.openForEdit(uuid)).thenThrow(new IOException("disk error"));

        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, editor, scheduler, server);

        verify(messages).send(viewer, "invsee.read-error");
        verify(gui, never()).open(any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Execution: offline commit path (triggered by window close)
    // -----------------------------------------------------------------------

    @Test
    void close_committed_stagesPreservingInventoryAndSendsSaved() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        UUID uuid = UUID.randomUUID();
        OfflinePlayer offline = offlineWithEnderItems(uuid, new ItemStack(Material.SHULKER_BOX));
        when(session.stage(any(), any()))
                .thenReturn(new StagedOfflineWrite(Path.of("a"), Path.of("b"), new byte[] {1}));
        when(session.commit(any(), any())).thenReturn(OfflineCommitResult.COMMITTED);

        Consumer<InventoryCloseEvent.Reason> onClose = openAndCaptureClose(viewer, offline);
        onClose.accept(InventoryCloseEvent.Reason.PLAYER);

        verify(session).stage(eq(OfflinePlayerSnapshot.empty().inventory()), any());
        verify(messages).send(viewer, "invsee.saved", "player", "ghost");
    }

    @Test
    void close_targetOnline_sendsCollisionMessage() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        UUID uuid = UUID.randomUUID();
        OfflinePlayer offline = offlineWithEnderItems(uuid, new ItemStack(Material.SHULKER_BOX));
        when(session.stage(any(), any()))
                .thenReturn(new StagedOfflineWrite(Path.of("a"), Path.of("b"), new byte[] {1}));
        when(session.commit(any(), any()))
                .thenReturn(OfflineCommitResult.ABORTED_TARGET_ONLINE);

        Consumer<InventoryCloseEvent.Reason> onClose = openAndCaptureClose(viewer, offline);
        onClose.accept(InventoryCloseEvent.Reason.PLAYER);

        verify(messages).send(viewer, "invsee.commit-player-online", "player", "ghost");
    }

    @Test
    void close_sourceChanged_sendsStaleSourceAbort() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        UUID uuid = UUID.randomUUID();
        OfflinePlayer offline = offlineWithEnderItems(uuid, new ItemStack(Material.SHULKER_BOX));
        when(session.stage(any(), any()))
                .thenReturn(new StagedOfflineWrite(Path.of("a"), Path.of("b"), new byte[] {1}));
        when(session.commit(any(), any()))
                .thenReturn(OfflineCommitResult.ABORTED_SOURCE_CHANGED);

        Consumer<InventoryCloseEvent.Reason> onClose = openAndCaptureClose(viewer, offline);
        onClose.accept(InventoryCloseEvent.Reason.PLAYER);

        verify(messages).send(viewer, "invsee.commit-source-changed", "player", "ghost");
    }

    @Test
    void close_stageFailure_sendsWriteErrorAndNeverCommits() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        UUID uuid = UUID.randomUUID();
        OfflinePlayer offline = offlineWithEnderItems(uuid, new ItemStack(Material.SHULKER_BOX));
        when(session.stage(any(), any())).thenThrow(new IOException("disk full"));

        Consumer<InventoryCloseEvent.Reason> onClose = openAndCaptureClose(viewer, offline);
        onClose.accept(InventoryCloseEvent.Reason.PLAYER);

        verify(messages).send(viewer, "invsee.write-error");
        verify(session, never()).commit(any(), any());
    }

    @Test
    void close_commitFailure_sendsWriteError() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        UUID uuid = UUID.randomUUID();
        OfflinePlayer offline = offlineWithEnderItems(uuid, new ItemStack(Material.SHULKER_BOX));
        when(session.stage(any(), any()))
                .thenReturn(new StagedOfflineWrite(Path.of("a"), Path.of("b"), new byte[] {1}));
        when(session.commit(any(), any())).thenThrow(new IOException("replace failed"));

        Consumer<InventoryCloseEvent.Reason> onClose = openAndCaptureClose(viewer, offline);
        onClose.accept(InventoryCloseEvent.Reason.PLAYER);

        verify(messages).send(viewer, "invsee.write-error");
    }

    @Test
    void close_commitPredicate_isFalseWhenTargetOnline() throws IOException {
        PlayerMock viewer = server.addPlayer("viewer");
        UUID uuid = UUID.randomUUID();
        OfflinePlayer offline = offlineWithEnderItems(uuid, new ItemStack(Material.SHULKER_BOX));
        when(session.stage(any(), any()))
                .thenReturn(new StagedOfflineWrite(Path.of("a"), Path.of("b"), new byte[] {1}));
        when(session.commit(any(), any())).thenReturn(OfflineCommitResult.COMMITTED);

        Server onlineServer = mock(Server.class);
        when(onlineServer.getPluginManager()).thenReturn(server.getPluginManager());
        Player onlineTarget = mock(Player.class);
        when(onlineServer.getPlayer(uuid)).thenReturn(onlineTarget);

        OfflinePlayerDataEditor localEditor = mock(OfflinePlayerDataEditor.class);
        when(localEditor.openForEdit(uuid)).thenReturn(Optional.of(session));
        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, localEditor, scheduler, onlineServer);
        Consumer<InventoryCloseEvent.Reason> onClose = captureClose();
        onClose.accept(InventoryCloseEvent.Reason.PLAYER);

        ArgumentCaptor<BooleanSupplier> predicate = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(session).commit(any(), predicate.capture());
        assertThat(predicate.getValue().getAsBoolean()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private OfflinePlayer offlineWithEnderItems(UUID uuid, ItemStack item) throws IOException {
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.hasPlayedBefore()).thenReturn(true);
        when(offline.getUniqueId()).thenReturn(uuid);
        when(editor.openForEdit(uuid)).thenReturn(Optional.of(session));
        when(session.snapshot())
                .thenReturn(
                        OfflinePlayerSnapshot.ofEnderItems(List.of(new SlottedItem(0, item))));
        return offline;
    }

    private Consumer<InventoryCloseEvent.Reason> openAndCaptureClose(
            Player viewer, OfflinePlayer offline) {
        EnderChestCommand.openOfflineEnderChest(
                viewer, offline, "ghost", gui, messages, editor, scheduler, server);
        return captureClose();
    }

    @SuppressWarnings("unchecked")
    private Consumer<InventoryCloseEvent.Reason> captureClose() {
        ArgumentCaptor<Consumer<InventoryCloseEvent.Reason>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(gui)
                .open(
                        any(),
                        any(xyz.xenondevs.invui.gui.Gui.class),
                        any(Component.class),
                        captor.capture());
        return captor.getValue();
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
