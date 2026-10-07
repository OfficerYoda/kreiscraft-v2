package de.kreiscraft.core.command;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/**
 * Builds a command that forwards to another node already present in the server dispatcher.
 *
 * <p>The returned literal carries the <em>target's own</em> authorization requirement and a
 * Brigadier redirect into the target subtree. As a result the alias inherits the target command's
 * exact syntax, completions, and permission checks — the same behaviour vanilla commands expose —
 * rather than re-implementing them. This is how {@code /gm} becomes an alias for {@code
 * minecraft:gamemode}.
 */
public final class RedirectCommand {

    private RedirectCommand() {}

    /**
     * Returns a literal named {@code alias} that redirects into {@code target}, reusing {@code
     * target.getRequirement()} so the alias enforces the exact same authorization as the command it
     * forwards to.
     */
    public static LiteralCommandNode<CommandSourceStack> build(
            String alias, CommandNode<CommandSourceStack> target) {
        return Commands.literal(alias)
                .requires(target.getRequirement())
                .redirect(target)
                .build();
    }
}
