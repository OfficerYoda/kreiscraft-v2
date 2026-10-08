package de.kreiscraft;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class ReloadCommand {

    private ReloadCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(ReloadService reload) {
        return Commands.literal("kreiscraft")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin"))
                .then(Commands.literal("reload").executes(ctx -> {
                    reload.reload(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }
}
