package de.kreiscraft.modules.worldmgmt.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.worldmgmt.Dimension;
import de.kreiscraft.modules.worldmgmt.DimensionAccessService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DimensionCommandTest {

    private DimensionAccessService dimensions;
    private MessageService messages;
    private CommandSender sender;
    private CommandSourceStack source;
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeEach
    void setUp() {
        dimensions = mock(DimensionAccessService.class);
        messages = mock(MessageService.class);
        sender = mock(CommandSender.class);
        source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        when(sender.hasPermission(DimensionCommand.PERMISSION)).thenReturn(true);
        when(messages.renderFor(any(), any())).thenReturn(Component.empty());

        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(DimensionCommand.build(dimensions, messages));
    }

    @Test
    void bareCommand_reportsBothDimensions() throws Exception {
        dispatcher.execute("dimension", source);

        verify(messages).send(
                sender, "world-mgmt.dimension-status", "dimension", "Nether", "state", Component.empty());
        verify(messages).send(
                sender, "world-mgmt.dimension-status", "dimension", "End", "state", Component.empty());
    }

    @Test
    void statusSubcommand_reportsBothDimensions() throws Exception {
        dispatcher.execute("dimension status", source);

        verify(messages).send(
                sender, "world-mgmt.dimension-status", "dimension", "Nether", "state", Component.empty());
        verify(messages).send(
                sender, "world-mgmt.dimension-status", "dimension", "End", "state", Component.empty());
    }

    @Test
    void dimensionWithoutAction_reportsThatDimensionOnly() throws Exception {
        dispatcher.execute("dimension nether", source);

        verify(messages).send(
                sender, "world-mgmt.dimension-status", "dimension", "Nether", "state", Component.empty());
        verify(messages, never()).send(
                sender, "world-mgmt.dimension-status", "dimension", "End", "state", Component.empty());
    }

    @Test
    void disable_persistsAndConfirms() throws Exception {
        when(dimensions.setAllowed(Dimension.NETHER, false)).thenReturn(true);

        dispatcher.execute("dimension nether disable", source);

        verify(dimensions).setAllowed(Dimension.NETHER, false);
        verify(messages).send(
                sender, "world-mgmt.dimension-disabled", "dimension", "Nether");
    }

    @Test
    void enable_persistsAndConfirms() throws Exception {
        when(dimensions.setAllowed(Dimension.END, true)).thenReturn(true);

        dispatcher.execute("dimension end enable", source);

        verify(dimensions).setAllowed(Dimension.END, true);
        verify(messages).send(sender, "world-mgmt.dimension-enabled", "dimension", "End");
    }

    @Test
    void failedWrite_reportsFailureInsteadOfSuccess() throws Exception {
        when(dimensions.setAllowed(Dimension.NETHER, false)).thenReturn(false);

        dispatcher.execute("dimension nether disable", source);

        verify(messages).send(sender, "world-mgmt.dimension-update-failed");
        verify(messages, never()).send(
                sender, "world-mgmt.dimension-disabled", "dimension", "Nether");
    }

    @Test
    void missingPermission_doesNotExecute() throws Exception {
        when(sender.hasPermission(DimensionCommand.PERMISSION)).thenReturn(false);

        try {
            dispatcher.execute("dimension nether disable", source);
        } catch (Exception e) {
            // Expected: the requirement rejects the command.
        }

        verify(dimensions, never()).setAllowed(Dimension.NETHER, false);
    }
}
