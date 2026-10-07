package de.kreiscraft.modules.updatemode.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.updatemode.UpdateModeService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateModeCommandTest {

    private UpdateModeService service;
    private MessageService messages;
    private CommandSender sender;
    private CommandSourceStack source;
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeEach
    void setUp() {
        service = mock(UpdateModeService.class);
        messages = mock(MessageService.class);
        sender = mock(CommandSender.class);
        source = mock(CommandSourceStack.class);

        when(source.getSender()).thenReturn(sender);
        when(sender.hasPermission("kreiscraft.updatemode.admin")).thenReturn(true);

        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(UpdateModeCommand.build(service, messages));
    }

    @Test
    void command_on_activatesService() throws Exception {
        dispatcher.execute("updatemode on", source);

        verify(service).setActive(true);
        verify(messages).send(sender, "update-mode.activated");
    }

    @Test
    void command_off_deactivatesService() throws Exception {
        dispatcher.execute("updatemode off", source);

        verify(service).setActive(false);
        verify(messages).send(sender, "update-mode.deactivated");
    }

    @Test
    void command_status_whenActive_sendsStatusOn() throws Exception {
        when(service.isActive()).thenReturn(true);

        dispatcher.execute("updatemode status", source);

        verify(messages).send(sender, "update-mode.status-on");
    }

    @Test
    void command_status_whenInactive_sendsStatusOff() throws Exception {
        when(service.isActive()).thenReturn(false);

        dispatcher.execute("updatemode status", source);

        verify(messages).send(sender, "update-mode.status-off");
    }

    @Test
    void command_requiresPermission_whenMissing_doesNotExecute() throws Exception {
        when(sender.hasPermission("kreiscraft.updatemode.admin")).thenReturn(false);

        // Brigadier throws CommandSyntaxException or returns 0 when requirements not met
        // The requires() predicate returns false, so the command is not dispatched
        try {
            dispatcher.execute("updatemode on", source);
        } catch (Exception e) {
            // Expected: permission not met
        }
        verify(service, never()).setActive(true);
    }
}
