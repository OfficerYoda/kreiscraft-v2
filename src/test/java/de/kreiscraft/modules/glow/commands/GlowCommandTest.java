package de.kreiscraft.modules.glow.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.glow.GlowService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class GlowCommandTest {

    @Mock
    private GlowService service;
    @Mock
    private MessageService messages;
    @Mock
    private Player player;
    @Mock
    private CommandSourceStack source;
    @Mock
    private CommandContext<CommandSourceStack> ctx;

    private LiteralCommandNode<CommandSourceStack> node;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        node = GlowCommand.build(service, messages);
        playerId = UUID.randomUUID();
    }

    @Test
    void execute_togglesGlow_andSendsEnabledMessage() throws Exception {
        when(player.getUniqueId()).thenReturn(playerId);
        when(source.getSender()).thenReturn(player);
        when(ctx.getSource()).thenReturn(source);
        when(service.isGlowing(playerId)).thenReturn(true);

        node.getCommand().run(ctx);

        verify(service).toggle(playerId);
        verify(messages).send(player, "glow.enabled");
    }

    @Test
    void execute_togglesGlow_andSendsDisabledMessage() throws Exception {
        when(player.getUniqueId()).thenReturn(playerId);
        when(source.getSender()).thenReturn(player);
        when(ctx.getSource()).thenReturn(source);
        when(service.isGlowing(playerId)).thenReturn(false);

        node.getCommand().run(ctx);

        verify(service).toggle(playerId);
        verify(messages).send(player, "glow.disabled");
    }

    @Test
    void requires_withPermission_returnsTrue() {
        when(source.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.glow.use")).thenReturn(true);

        assertThat(node.getRequirement().test(source)).isTrue();
    }

    @Test
    void requires_withoutPermission_returnsFalse() {
        when(source.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.glow.use")).thenReturn(false);

        assertThat(node.getRequirement().test(source)).isFalse();
    }

    @Test
    void execute_whenSenderIsNotPlayer_returnsSuccess() throws Exception {
        CommandSender consoleSender = mock(CommandSender.class);
        when(source.getSender()).thenReturn(consoleSender);
        when(ctx.getSource()).thenReturn(source);

        int result = node.getCommand().run(ctx);

        assertThat(result).isEqualTo(Command.SINGLE_SUCCESS);
    }
}
