package de.kreiscraft.modules.invsee.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionTestSupport;
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
        when(messages.renderFor(any(), anyString(), any(Object[].class)))
                .thenReturn(Component.text("title"));
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
                InvseeCommand.build(gui, messages, offlineReader, server);
        assertThat(node.getLiteral()).isEqualTo("invsee");
    }

    @Test
    void build_requiresInvseeUsePermission_deniedWithoutPerm() {
        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader, server);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        Player viewer = server.addPlayer();
        when(mockSrc.getSender()).thenReturn(viewer);
        assertThat(node.getRequirement().test(mockSrc)).isFalse();
    }

    @Test
    void build_requiresInvseeUsePermission_allowedWithPerm() {
        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader, server);
        CommandSourceStack mockSrc = mock(CommandSourceStack.class);
        PlayerMock viewer = server.addPlayer();
        viewer.addAttachment(plugin, "kreiscraft.invsee.use", true);
        when(mockSrc.getSender()).thenReturn(viewer);
        assertThat(node.getRequirement().test(mockSrc)).isTrue();
    }

    @Test
    void build_hasTargetArgument() {
        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader, server);
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
        OfflinePlayer neverPlayed = mock(OfflinePlayer.class);
        when(neverPlayed.hasPlayedBefore()).thenReturn(false);
        when(suggestionServer.getOfflinePlayers())
                .thenReturn(new OfflinePlayer[] {known, neverPlayed});

        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader, suggestionServer);
        CommandSourceStack source = mock(CommandSourceStack.class);
        PlayerMock viewer = server.addPlayer();
        viewer.addAttachment(plugin, "kreiscraft.invsee.use", true);
        when(source.getSender()).thenReturn(viewer);

        CommandNode<CommandSourceStack> target = node.getChildren().iterator().next();
        List<String> suggestions = SuggestionTestSupport.suggestions(target, source, "");

        assertThat(suggestions).containsExactly("Alice", "Carol");
    }

    @Test
    void build_targetSuggestsNothingWithoutPermission() {
        Server suggestionServer = mock(Server.class);
        when(suggestionServer.getOnlinePlayers()).thenAnswer(invocation -> List.of());

        LiteralCommandNode<CommandSourceStack> node =
                InvseeCommand.build(gui, messages, offlineReader, suggestionServer);
        CommandSourceStack source = mock(CommandSourceStack.class);
        Player viewer = server.addPlayer();
        when(source.getSender()).thenReturn(viewer);

        CommandNode<CommandSourceStack> target = node.getChildren().iterator().next();
        List<String> suggestions = SuggestionTestSupport.suggestions(target, source, "");

        assertThat(suggestions).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Execution: online path
    // -----------------------------------------------------------------------

    @Test
    void openOnlineInventory_opensEditableGuiWithAllPlayerSlots() {
        PlayerMock viewer = server.addPlayer("viewer");
        PlayerMock target = server.addPlayer("target");

        InvseeCommand.openOnlineInventory(viewer, target, gui, messages);

        ArgumentCaptor<xyz.xenondevs.invui.gui.Gui> captor =
                ArgumentCaptor.forClass(xyz.xenondevs.invui.gui.Gui.class);
        verify(gui).open(eq(viewer), captor.capture(), any(Component.class));
        long boundSlots =
                captor.getValue().getSize() == 45
                        ? countInventoryLinks(captor.getValue())
                        : -1;
        assertThat(boundSlots).isEqualTo(41);
    }

    @Test
    void openOnlineInventory_rendersLocalizedTitleForViewer() {
        PlayerMock viewer = server.addPlayer("viewer");
        PlayerMock target = server.addPlayer("target");

        InvseeCommand.openOnlineInventory(viewer, target, gui, messages);

        verify(messages)
                .renderFor(eq(viewer), eq("invsee.gui-title-inv"), eq("player"), eq("target"));
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
        verify(messages)
                .renderFor(eq(viewer), eq("invsee.gui-title-inv"), eq("player"), eq("ghost"));
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
