package de.kreiscraft.modules.positions.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.positions.PositionEntry;
import de.kreiscraft.modules.positions.PositionsConfig;
import de.kreiscraft.modules.positions.PositionsService;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
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
class PositionCommandTest {

    @Mock
    private PositionsService service;

    @Mock
    private MessageService messages;

    @Mock
    private Gui gui;

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static PositionsConfig defaultConfig() {
        return new PositionsConfig(6, true);
    }

    private CommandDispatcher<CommandSourceStack> buildDispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(
                        PositionCommand.build(
                                service, messages, gui, PositionCommandTest::defaultConfig));
        return dispatcher;
    }

    private CommandSourceStack sourceFor(Player player) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        return source;
    }

    @Test
    void commandNode_hasExpectedName() {
        LiteralCommandNode<CommandSourceStack> node =
                PositionCommand.build(
                        service, messages, gui, PositionCommandTest::defaultConfig);
        assertThat(node.getName()).isEqualTo("pos");
    }

    @Test
    void commandNode_hasExpectedSubcommands() {
        LiteralCommandNode<CommandSourceStack> node =
                PositionCommand.build(
                        service, messages, gui, PositionCommandTest::defaultConfig);
        assertThat(node.getChild("set")).isNotNull();
        assertThat(node.getChild("get")).isNotNull();
        assertThat(node.getChild("delete")).isNotNull();
        assertThat(node.getChild("list")).isNotNull();
    }

    @Test
    void set_withPermission_callsServiceAndSendsMessage() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.positions.use", true);
        // service.set should be called with the player's location
        // We don't stub service.set() since it has void return — Mockito does nothing by default

        buildDispatcher().execute("pos set home", sourceFor(player));

        verify(service).set(
                eq(player.getUniqueId()),
                eq("home"),
                any(Location.class),
                eq(player.getUniqueId()),
                eq("Alice"));
        verify(messages).send(player, "positions.set", "name", "home");
    }

    @Test
    void set_withoutPermission_doesNotCallService() {
        PlayerMock player = server.addPlayer("Alice");
        // No permission

        try {
            buildDispatcher().execute("pos set home", sourceFor(player));
        } catch (CommandSyntaxException ignored) {
            // Brigadier rejects when requires() fails
        }

        verify(service, never()).set(any(UUID.class), anyString(), any(Location.class));
    }

    @Test
    void get_whenEntryExists_sendsListEntry() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.positions.use", true);
        Location loc = new Location(server.getWorld("world"), 10, 64, -5);
        when(service.get(player.getUniqueId(), "home")).thenReturn(Optional.of(new PositionEntry(loc)));

        buildDispatcher().execute("pos get home", sourceFor(player));

        verify(messages).send(
                eq(player),
                eq("positions.list-entry"),
                eq("name"), eq("home"),
                eq("world"), eq("world"),
                eq("x"), eq("10"),
                eq("y"), eq("64"),
                eq("z"), eq("-5"));
    }

    @Test
    void get_whenEntryNotFound_sendsNotFound() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.positions.use", true);
        when(service.get(player.getUniqueId(), "missing")).thenReturn(Optional.empty());

        buildDispatcher().execute("pos get missing", sourceFor(player));

        verify(messages).send(player, "positions.not-found", "name", "missing");
    }

    @Test
    void delete_whenEntryExists_removesAndSendsMessage() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.positions.use", true);
        Location loc = new Location(server.getWorld("world"), 0, 64, 0);
        when(service.get(player.getUniqueId(), "base")).thenReturn(Optional.of(new PositionEntry(loc)));

        buildDispatcher().execute("pos delete base", sourceFor(player));

        verify(service).remove(player.getUniqueId(), "base");
        verify(messages).send(player, "positions.removed", "name", "base");
    }

    @Test
    void delete_whenNotFound_sendsNotFound() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.positions.use", true);
        when(service.get(player.getUniqueId(), "ghost")).thenReturn(Optional.empty());

        buildDispatcher().execute("pos delete ghost", sourceFor(player));

        verify(service, never()).remove(any(UUID.class), anyString());
        verify(messages).send(player, "positions.not-found", "name", "ghost");
    }

    @Test
    void list_sendsHeaderAndEntries() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.positions.use", true);
        Location loc = new Location(server.getWorld("world"), 5, 64, 5);
        Map<String, PositionEntry> positions = new LinkedHashMap<>();
        positions.put("home", new PositionEntry(loc));
        when(service.forPlayer(player.getUniqueId())).thenReturn(positions);

        buildDispatcher().execute("pos list", sourceFor(player));

        verify(messages).send(player, "positions.list-header", "count", "1");
        verify(messages).send(
                eq(player),
                eq("positions.list-entry"),
                eq("name"), eq("home"),
                eq("world"), eq("world"),
                eq("x"), anyString(),
                eq("y"), anyString(),
                eq("z"), anyString());
    }

    @Test
    void noArg_opensGui() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.positions.use", true);
        when(service.forPlayer(player.getUniqueId())).thenReturn(Map.of());
        when(messages.renderFor(player, "positions.gui-title")).thenReturn(Component.text("Positions"));

        buildDispatcher().execute("pos", sourceFor(player));

        verify(gui).open(eq(player), any(xyz.xenondevs.invui.gui.Gui.class), any(Component.class));
    }
}
