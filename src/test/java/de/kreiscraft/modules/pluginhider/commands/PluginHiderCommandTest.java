package de.kreiscraft.modules.pluginhider.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.pluginhider.PluginHiderService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PluginHiderCommandTest {

    @Mock
    private PluginHiderService service;

    @Mock
    private MessageService messages;

    @Mock
    private CommandSourceStack source;

    @Mock
    private CommandSender sender;

    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeEach
    void setUp() {
        when(source.getSender()).thenReturn(sender);
        when(sender.hasPermission("kreiscraft.pluginhider.admin")).thenReturn(true);

        LiteralCommandNode<CommandSourceStack> node = PluginHiderCommand.build(service, messages);
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
    }

    // ── add ───────────────────────────────────────────────────────────────

    @Test
    void add_whenNewCommand_callsServiceAddAndSendsAdded() throws CommandSyntaxException {
        when(service.add("tps")).thenReturn(true);

        dispatcher.execute("pluginhider add tps", source);

        verify(service).add("tps");
        verify(messages).send(sender, "plugin-hider.added", "command", "tps");
    }

    @Test
    void add_whenAlreadyPresent_sendsAlreadyPresent() throws CommandSyntaxException {
        when(service.add("tps")).thenReturn(false);

        dispatcher.execute("pluginhider add tps", source);

        verify(messages).send(sender, "plugin-hider.already-present", "command", "tps");
    }

    // ── remove ────────────────────────────────────────────────────────────

    @Test
    void remove_whenPresent_callsServiceRemoveAndSendsRemoved() throws CommandSyntaxException {
        when(service.remove("spawn")).thenReturn(true);

        dispatcher.execute("pluginhider remove spawn", source);

        verify(service).remove("spawn");
        verify(messages).send(sender, "plugin-hider.removed", "command", "spawn");
    }

    @Test
    void remove_whenNotPresent_sendsNotPresent() throws CommandSyntaxException {
        when(service.remove("spawn")).thenReturn(false);

        dispatcher.execute("pluginhider remove spawn", source);

        verify(messages).send(sender, "plugin-hider.not-present", "command", "spawn");
    }

    // ── list ──────────────────────────────────────────────────────────────

    @Test
    void list_populated_sendsHeaderThenOneEntryPerCommand() throws CommandSyntaxException {
        when(service.allowedCommands()).thenReturn(List.of("help", "spawn"));

        dispatcher.execute("pluginhider list", source);

        verify(messages).send(sender, "plugin-hider.list-header");
        verify(messages).send(sender, "plugin-hider.list-entry", "command", "help");
        verify(messages).send(sender, "plugin-hider.list-entry", "command", "spawn");
    }

    @Test
    void list_empty_sendsHeaderThenListEmpty() throws CommandSyntaxException {
        when(service.allowedCommands()).thenReturn(List.of());

        dispatcher.execute("pluginhider list", source);

        verify(messages).send(sender, "plugin-hider.list-header");
        verify(messages).send(sender, "plugin-hider.list-empty");
    }

    // ── permission gate ───────────────────────────────────────────────────

    @Test
    void add_withoutPermission_doesNotCallService() throws CommandSyntaxException {
        when(sender.hasPermission("kreiscraft.pluginhider.admin")).thenReturn(false);

        try {
            dispatcher.execute("pluginhider add tps", source);
        } catch (CommandSyntaxException ignored) {
            // Brigadier throws when requires() returns false
        }

        verify(service, never()).add("tps");
    }
}
