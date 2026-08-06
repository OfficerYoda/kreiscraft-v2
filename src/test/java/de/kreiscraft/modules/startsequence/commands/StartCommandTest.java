package de.kreiscraft.modules.startsequence.commands;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.startsequence.StartSequenceService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartCommandTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CommandDispatcher<CommandSourceStack> dispatcherWith(
            LiteralCommandNode<CommandSourceStack> node) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        return dispatcher;
    }

    private CommandSourceStack sourceFor(Player player) {
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        return src;
    }

    @Test
    void start_whenNotStarted_triggersService() throws Exception {
        StartSequenceService service = mock(StartSequenceService.class);
        MessageService messages = mock(MessageService.class);
        when(service.hasStarted()).thenReturn(false);

        Player player = server.addPlayer("TestPlayer");
        player.addAttachment(MockBukkit.createMockPlugin(), "kreiscraft.startsequence.admin", true);
        World world = player.getWorld();

        CommandSourceStack src = sourceFor(player);
        CommandDispatcher<CommandSourceStack> dispatcher =
                dispatcherWith(StartCommand.build(service, messages));

        dispatcher.execute("start", src);

        verify(service).trigger(world);
        verify(messages, never()).send(player, "start-sequence.already-started");
    }

    @Test
    void start_whenAlreadyStarted_sendsAlreadyStartedMessage() throws Exception {
        StartSequenceService service = mock(StartSequenceService.class);
        MessageService messages = mock(MessageService.class);
        when(service.hasStarted()).thenReturn(true);

        Player player = server.addPlayer("TestPlayer");
        player.addAttachment(MockBukkit.createMockPlugin(), "kreiscraft.startsequence.admin", true);

        CommandSourceStack src = sourceFor(player);
        CommandDispatcher<CommandSourceStack> dispatcher =
                dispatcherWith(StartCommand.build(service, messages));

        dispatcher.execute("start", src);

        verify(messages).send(player, "start-sequence.already-started");
        verify(service, never()).trigger(player.getWorld());
    }

    @Test
    void startReset_whenStarted_callsResetAndSendsMessage() throws Exception {
        StartSequenceService service = mock(StartSequenceService.class);
        MessageService messages = mock(MessageService.class);
        when(service.hasStarted()).thenReturn(true);

        Player player = server.addPlayer("TestPlayer");
        player.addAttachment(MockBukkit.createMockPlugin(), "kreiscraft.startsequence.admin", true);

        CommandSourceStack src = sourceFor(player);
        CommandDispatcher<CommandSourceStack> dispatcher =
                dispatcherWith(StartCommand.build(service, messages));

        dispatcher.execute("start reset", src);

        verify(service).reset();
        verify(messages).send(player, "start-sequence.reset");
    }

    @Test
    void startReset_whenNotStarted_sendsNotStartedMessage() throws Exception {
        StartSequenceService service = mock(StartSequenceService.class);
        MessageService messages = mock(MessageService.class);
        when(service.hasStarted()).thenReturn(false);

        Player player = server.addPlayer("TestPlayer");
        player.addAttachment(MockBukkit.createMockPlugin(), "kreiscraft.startsequence.admin", true);

        CommandSourceStack src = sourceFor(player);
        CommandDispatcher<CommandSourceStack> dispatcher =
                dispatcherWith(StartCommand.build(service, messages));

        dispatcher.execute("start reset", src);

        verify(messages).send(player, "start-sequence.not-started");
        verify(service, never()).reset();
    }

    @Test
    void start_withoutPermission_requiresFails() {
        StartSequenceService service = mock(StartSequenceService.class);
        MessageService messages = mock(MessageService.class);

        Player player = server.addPlayer("TestPlayer");
        // No permission granted

        CommandSourceStack src = sourceFor(player);
        LiteralCommandNode<CommandSourceStack> node = StartCommand.build(service, messages);

        // The requires predicate should reject a sender without permission
        boolean allowed = node.getRequirement().test(src);
        verify(service, never()).trigger(player.getWorld());
        assert !allowed : "should be rejected without permission";
    }
}
