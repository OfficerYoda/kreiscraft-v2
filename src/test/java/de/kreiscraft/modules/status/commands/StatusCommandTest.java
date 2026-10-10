package de.kreiscraft.modules.status.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.status.StatusService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.UUID;
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
class StatusCommandTest {

    @Mock
    private StatusService service;

    @Mock
    private MessageService messages;

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

    private CommandDispatcher<CommandSourceStack> buildDispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(StatusCommand.build(service, messages));
        return dispatcher;
    }

    private CommandSourceStack sourceFor(Player player) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        return source;
    }

    @Test
    void commandNode_hasExpectedName() {
        LiteralCommandNode<CommandSourceStack> node = StatusCommand.build(service, messages);
        assertThat(node.getName()).isEqualTo("status");
    }

    @Test
    void commandNode_hasPrefixAndSuffixSubcommands() {
        LiteralCommandNode<CommandSourceStack> node = StatusCommand.build(service, messages);
        assertThat(node.getChild("prefix")).isNotNull();
        assertThat(node.getChild("suffix")).isNotNull();
    }

    @Test
    void commandNode_prefixExposesOnlySelfEditingTextArgument() {
        LiteralCommandNode<CommandSourceStack> node = StatusCommand.build(service, messages);
        assertThat(node.getChild("prefix").getChild("text")).isNotNull();
        assertThat(node.getChild("prefix").getChild("target")).isNull();
    }

    @Test
    void commandNode_suffixExposesOnlySelfEditingTextArgument() {
        LiteralCommandNode<CommandSourceStack> node = StatusCommand.build(service, messages);
        assertThat(node.getChild("suffix").getChild("text")).isNotNull();
        assertThat(node.getChild("suffix").getChild("target")).isNull();
    }

    @Test
    void tabComplete_afterStatus_suggestsPrefixAndSuffix() {
        // Brigadier lists literal-child suggestions without evaluating requires() predicates,
        // so no getSender() stub needed here.
        CommandSourceStack source = mock(CommandSourceStack.class);

        CommandDispatcher<CommandSourceStack> dispatcher = buildDispatcher();
        ParseResults<CommandSourceStack> parsed = dispatcher.parse("status ", source);
        Suggestions suggestions = dispatcher.getCompletionSuggestions(parsed).join();

        List<String> completions =
                suggestions.getList().stream().map(s -> s.getText()).toList();
        assertThat(completions).containsExactlyInAnyOrder("prefix", "suffix");
    }

    @Test
    void setPrefix_withPermission_callsServiceAndSendsMessage() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("TestPlayer");
        player.addAttachment(plugin, "kreiscraft.status.set.self", true);

        buildDispatcher().execute("status prefix Hello World", sourceFor(player));

        verify(service).setPrefix(player.getUniqueId(), "Hello World");
        verify(messages).send(player, "status.prefix-set");
    }

    @Test
    void setSuffix_withPermission_callsServiceAndSendsMessage() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("TestPlayer");
        player.addAttachment(plugin, "kreiscraft.status.set.self", true);

        buildDispatcher().execute("status suffix [VIP]", sourceFor(player));

        verify(service).setSuffix(player.getUniqueId(), "[VIP]");
        verify(messages).send(player, "status.suffix-set");
    }

    @Test
    void setPrefix_withoutPermission_doesNotCallService() {
        PlayerMock player = server.addPlayer("TestPlayer");
        // No permission granted

        try {
            buildDispatcher().execute("status prefix Hello", sourceFor(player));
        } catch (CommandSyntaxException ignored) {
            // Brigadier rejects the command when requires() fails
        }

        verify(service, never()).setPrefix(any(UUID.class), any(String.class));
    }

    @Test
    void setPrefix_tooLong_sendsErrorAndDoesNotCallService() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("TestPlayer");
        player.addAttachment(plugin, "kreiscraft.status.set.self", true);

        String longText = "A".repeat(33); // 33 visible chars > max 32
        buildDispatcher().execute("status prefix " + longText, sourceFor(player));

        verify(service, never()).setPrefix(any(UUID.class), any(String.class));
        verify(messages).send(player, "status.too-long");
    }
}
