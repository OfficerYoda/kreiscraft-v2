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
import de.kreiscraft.modules.bedrock.BedrockAuthorizationResult;
import de.kreiscraft.modules.bedrock.BedrockRecord;
import de.kreiscraft.modules.bedrock.BedrockService;
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
    private MessageService messages;
    private CommandSourceStack source;
    private CommandSender sender;
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeEach
    void setUp() {
        service = mock(BedrockService.class);
        messages = mock(MessageService.class);
        sender = mock(CommandSender.class);
        source = mock(CommandSourceStack.class);

        when(source.getSender()).thenReturn(sender);
        when(sender.hasPermission("kreiscraft.bedrock.admin")).thenReturn(true);

        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(BedrockListCommand.build(service, messages));
    }

    @Test
    void add_registersPendingAuthorization() throws CommandSyntaxException {
        when(service.register(".steve")).thenReturn(BedrockAuthorizationResult.REGISTERED);

        dispatcher.execute("bedrocklist add .steve", source);

        verify(service).register(".steve");
        verify(messages).send(sender, "bedrock.authorized", "player", ".steve");
    }

    @Test
    void add_whenAlreadyAuthorized_reportsExisting() throws CommandSyntaxException {
        when(service.register(".steve")).thenReturn(BedrockAuthorizationResult.ALREADY_REGISTERED);

        dispatcher.execute("bedrocklist add .steve", source);

        verify(messages).send(sender, "bedrock.already-authorized", "player", ".steve");
    }

    @Test
    void remove_whenAuthorized_revokes() throws CommandSyntaxException {
        when(service.remove(".steve")).thenReturn(BedrockAuthorizationResult.REMOVED);

        dispatcher.execute("bedrocklist remove .steve", source);

        verify(service).remove(".steve");
        verify(messages).send(sender, "bedrock.revoked", "player", ".steve");
    }

    @Test
    void remove_whenNotAuthorized_reportsMissing() throws CommandSyntaxException {
        when(service.remove(".nobody")).thenReturn(BedrockAuthorizationResult.NOT_FOUND);

        dispatcher.execute("bedrocklist remove .nobody", source);

        verify(messages).send(sender, "bedrock.not-authorized", "player", ".nobody");
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
}
