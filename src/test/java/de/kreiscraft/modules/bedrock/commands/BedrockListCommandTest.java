package de.kreiscraft.modules.bedrock.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.bedrock.BedrockRecord;
import de.kreiscraft.modules.bedrock.BedrockService;
import de.kreiscraft.shared.whitelist.WhitelistService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class BedrockListCommandTest {

    private BedrockService service;
    private WhitelistService whitelist;
    private MessageService messages;
    private CommandSourceStack source;
    private CommandSender sender;
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeEach
    void setUp() {
        service = mock(BedrockService.class);
        whitelist = mock(WhitelistService.class);
        messages = mock(MessageService.class);
        sender = mock(CommandSender.class);
        source = mock(CommandSourceStack.class);

        when(source.getSender()).thenReturn(sender);
        when(sender.hasPermission("kreiscraft.bedrock.admin")).thenReturn(true);

        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(
                BedrockListCommand.build(service, whitelist, messages));
    }

    @Test
    void add_whenPlayerKnown_whitelists() throws CommandSyntaxException {
        UUID uuid = UUID.randomUUID();
        when(service.knownPlayers()).thenReturn(
                List.of(new BedrockRecord(".steve", uuid, Instant.now())));

        dispatcher.execute("bedrocklist add .steve", source);

        verify(whitelist).addBedrockPlayer(".steve", uuid);
        verify(messages).send(sender, "bedrock.added", "player", ".steve");
    }

    @Test
    void add_whenPlayerUnknown_sendsUnknownMessage() throws CommandSyntaxException {
        when(service.knownPlayers()).thenReturn(List.of());

        dispatcher.execute("bedrocklist add .nobody", source);

        verify(whitelist, never()).addBedrockPlayer(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(messages).send(sender, "bedrock.unknown", "player", ".nobody");
    }

    @Test
    void list_whenPopulated_sendsHeaderAndSortedNames() throws CommandSyntaxException {
        when(service.knownPlayers()).thenReturn(
                List.of(new BedrockRecord(".bob", UUID.randomUUID(), Instant.now()),
                        new BedrockRecord(".Alice", UUID.randomUUID(), Instant.now()),
                        new BedrockRecord(".carol", UUID.randomUUID(), Instant.now())));

        dispatcher.execute("bedrocklist list", source);

        InOrder order = inOrder(messages);
        order.verify(messages).send(sender, "bedrock.list-header", "count", "3");
        order.verify(messages).send(sender, "bedrock.list-entry", "player", ".Alice");
        order.verify(messages).send(sender, "bedrock.list-entry", "player", ".bob");
        order.verify(messages).send(sender, "bedrock.list-entry", "player", ".carol");
        order.verify(messages, never()).send(sender, "bedrock.list-empty");
    }

    @Test
    void list_whenEmpty_sendsHeaderWithZeroAndEmptyMarker() throws CommandSyntaxException {
        when(service.knownPlayers()).thenReturn(List.of());

        dispatcher.execute("bedrocklist list", source);

        verify(messages).send(sender, "bedrock.list-header", "count", "0");
        verify(messages).send(sender, "bedrock.list-empty");
        verify(messages, never()).send(eq(sender), eq("bedrock.list-entry"), any(), any());
    }

    @Test
    void requiresPermission_nodeHasRequiresPredicate() {
        when(sender.hasPermission("kreiscraft.bedrock.admin")).thenReturn(false);
        // The requires() predicate should deny access
        var node = dispatcher.getRoot().getChild("bedrocklist");
        assertThat(node).isNotNull();
        assertThat(node.canUse(source)).isFalse();
    }

    @Test
    void add_caseInsensitiveLookup() throws CommandSyntaxException {
        UUID uuid = UUID.randomUUID();
        // stored name has different case
        when(service.knownPlayers()).thenReturn(
                List.of(new BedrockRecord(".Steve", uuid, Instant.now())));

        dispatcher.execute("bedrocklist add .steve", source);

        verify(whitelist).addBedrockPlayer(".Steve", uuid);
    }
}
