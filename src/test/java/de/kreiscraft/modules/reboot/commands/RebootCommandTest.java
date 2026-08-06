package de.kreiscraft.modules.reboot.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.reboot.RebootConfig;
import de.kreiscraft.modules.reboot.RebootService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RebootCommandTest {

    @Mock
    private RebootService service;

    @Mock
    private MessageService messages;

    @Mock
    private CommandSourceStack source;

    @Mock
    private CommandSender sender;

    private RebootConfig config;
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeEach
    void setUp() {
        config = new RebootConfig(
                Duration.ofSeconds(60),
                List.of(500, 60, 30, 10),
                List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1));
        when(source.getSender()).thenReturn(sender);
        when(sender.hasPermission("kreiscraft.reboot.admin")).thenReturn(true);

        LiteralCommandNode<CommandSourceStack> node = RebootCommand.build(service, messages, config);
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
    }

    @Test
    void reboot_noArgs_startsWithDefaultCountdown() throws CommandSyntaxException {
        dispatcher.execute("reboot", source);
        verify(service).start(eq(Duration.ofSeconds(60)), eq(""));
    }

    @Test
    void reboot_withSeconds_startsWithGivenSeconds() throws CommandSyntaxException {
        dispatcher.execute("reboot 30", source);
        verify(service).start(eq(Duration.ofSeconds(30)), eq(""));
    }

    @Test
    void reboot_withSecondsAndReason_startsWithReason() throws CommandSyntaxException {
        dispatcher.execute("reboot 45 planned maintenance", source);
        verify(service).start(eq(Duration.ofSeconds(45)), eq("planned maintenance"));
    }

    @Test
    void reboot_cancel_whenRunning_callsCancel() throws CommandSyntaxException {
        when(service.isRunning()).thenReturn(true);
        dispatcher.execute("reboot cancel", source);
        verify(service).cancel();
    }

    @Test
    void reboot_cancel_whenNotRunning_sendsNotRunningMessage() throws CommandSyntaxException {
        when(service.isRunning()).thenReturn(false);
        dispatcher.execute("reboot cancel", source);
        verify(messages).send(sender, "reboot.not-running");
        verify(service, never()).cancel();
    }

    @Test
    void reboot_withoutPermission_doesNotExecute() {
        when(sender.hasPermission("kreiscraft.reboot.admin")).thenReturn(false);
        boolean threw = false;
        try {
            dispatcher.execute("reboot", source);
        } catch (CommandSyntaxException e) {
            threw = true;
        }
        assertThat(threw).isTrue();
        verify(service, never()).start(any(), any());
    }
}
