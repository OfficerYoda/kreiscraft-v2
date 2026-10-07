package de.kreiscraft.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a redirect command forwards to the target node's subtree while enforcing the
 * target's own authorization — the behaviour {@code /gm} relies on to alias {@code
 * minecraft:gamemode}.
 */
class RedirectCommandTest {

    private static final String PERMISSION = "minecraft.command.gamemode";

    private static CommandSourceStack source(boolean permitted) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(PERMISSION)).thenReturn(permitted);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        return source;
    }

    private static CommandDispatcher<CommandSourceStack> dispatcherWithVanilla(AtomicBoolean ran) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandNode<CommandSourceStack> vanilla =
                Commands.literal("gamemode")
                        .requires(src -> src.getSender().hasPermission(PERMISSION))
                        .then(Commands.literal("creative").executes(ctx -> {
                            ran.set(true);
                            return 1;
                        }))
                        .build();
        dispatcher.getRoot().addChild(vanilla);
        dispatcher.getRoot().addChild(RedirectCommand.build("gm", vanilla));
        return dispatcher;
    }

    @Test
    void build_redirectsToTheTargetNodeAndInheritsItsRequirement() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandNode<CommandSourceStack> vanilla =
                Commands.literal("gamemode")
                        .requires(src -> src.getSender().hasPermission(PERMISSION))
                        .build();

        LiteralCommandNode<CommandSourceStack> gm = RedirectCommand.build("gm", vanilla);

        assertThat(gm.getLiteral()).isEqualTo("gm");
        assertThat(gm.getRedirect()).isSameAs(vanilla);
        assertThat(gm.getRequirement()).isSameAs(vanilla.getRequirement());
        assertThat(gm.getChildren()).isEmpty();
    }

    @Test
    void dispatcher_executesAliasUsingInheritedSubtreeWhenAuthorized() throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherWithVanilla(ran);

        int result = dispatcher.execute("gm creative", source(true));

        assertThat(ran).as("the vanilla subtree must run through the alias").isTrue();
        assertThat(result).isEqualTo(1);
    }

    @Test
    void dispatcher_rejectsAliasWhenTargetRequirementFails() {
        AtomicBoolean ran = new AtomicBoolean(false);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherWithVanilla(ran);

        assertThatThrownBy(() -> dispatcher.execute("gm creative", source(false)))
                .as("the alias must enforce the vanilla command's authorization")
                .isInstanceOf(CommandSyntaxException.class);
        assertThat(ran).isFalse();
    }
}
