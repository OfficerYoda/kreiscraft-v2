package de.kreiscraft.modules.effects.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.effects.EffectsService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class EffectsCommandTest {

    @Test
    void build_commandNameIsEffects() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(mock(EffectsService.class), mock(MessageService.class));
        assertThat(node.getName()).isEqualTo("effects");
    }

    @Test
    void build_requiresPermission_deniesWithoutPermission() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(mock(EffectsService.class), mock(MessageService.class));

        CommandSourceStack src = mock(CommandSourceStack.class);
        Player player = mock(Player.class);
        when(src.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.effects.use")).thenReturn(false);

        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_allowsWithPermission() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(mock(EffectsService.class), mock(MessageService.class));

        CommandSourceStack src = mock(CommandSourceStack.class);
        Player player = mock(Player.class);
        when(src.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.effects.use")).thenReturn(true);

        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @Test
    void build_hasParticleChildArgument() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(mock(EffectsService.class), mock(MessageService.class));

        assertThat(node.getChildren()).isNotEmpty();
    }
}
