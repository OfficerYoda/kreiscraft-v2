package de.kreiscraft;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class ReloadCommand {

    private ReloadCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            ConfigService cfg, ModuleRegistry modules, MessageService messages) {
        return Commands.literal("kreiscraft")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin"))
                .then(Commands.literal("reload").executes(ctx -> {
                    try {
                        cfg.reload();
                        messages.reload();
                        modules.reloadConfigs(cfg.root());
                        messages.send(ctx.getSource().getSender(), "core.reload.success");
                    } catch (Exception e) {
                        messages.send(ctx.getSource().getSender(), "core.reload.failure",
                                "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }
}
