package de.kreiscraft.modules.sit.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.sit.SitService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SitCommandTest {

    private final SitService service = mock(SitService.class);
    private final MessageService messages = mock(MessageService.class);
    private final CommandSourceStack source = mock(CommandSourceStack.class);
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        dispatcher.getRoot().addChild(SitCommand.build(service, messages));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void playerCommand_togglesSitting() throws Exception {
        Player player = mock(Player.class);
        when(source.getSender()).thenReturn(player);
        when(player.hasPermission(SitCommand.PERMISSION)).thenReturn(true);

        assertThat(dispatcher.execute("sit", source)).isEqualTo(1);

        verify(service).toggle(player);
    }

    @Test
    void withoutPermission_cannotToggle() {
        when(source.getSender()).thenReturn(mock(Player.class));

        assertThatThrownBy(() -> dispatcher.execute("sit", source)).isInstanceOf(CommandSyntaxException.class);

        verifyNoInteractions(service);
    }

    @Test
    void console_receivesPlayerOnlyMessage() throws Exception {
        CommandSender console = mock(CommandSender.class);
        when(source.getSender()).thenReturn(console);
        when(console.hasPermission(SitCommand.PERMISSION)).thenReturn(true);

        dispatcher.execute("sit", source);

        verify(messages).send(console, "core.player-only");
        verifyNoInteractions(service);
    }
}
