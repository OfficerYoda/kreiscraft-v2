package de.kreiscraft.modules.updatemode.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.updatemode.UpdateModeService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class UpdateModeCommand {

    private UpdateModeCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            UpdateModeService service, MessageService messages) {
        return Commands.literal("updatemode")
                .requires(src -> src.getSender().hasPermission("kreiscraft.updatemode.admin"))
                .then(Commands.literal("on")
                        .executes(ctx -> activate(ctx.getSource(), service, messages)))
                .then(Commands.literal("off")
                        .executes(ctx -> deactivate(ctx.getSource(), service, messages)))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), service, messages)))
                .build();
    }

    private static int activate(
            CommandSourceStack source, UpdateModeService service, MessageService messages) {
        service.setActive(true);
        messages.send(source.getSender(), "update-mode.activated");
        return Command.SINGLE_SUCCESS;
    }

    private static int deactivate(
            CommandSourceStack source, UpdateModeService service, MessageService messages) {
        service.setActive(false);
        messages.send(source.getSender(), "update-mode.deactivated");
        return Command.SINGLE_SUCCESS;
    }

    private static int status(
            CommandSourceStack source, UpdateModeService service, MessageService messages) {
        boolean active = service.isActive();
        messages.send(source.getSender(), active ? "update-mode.status-on" : "update-mode.status-off");
        return Command.SINGLE_SUCCESS;
    }
}
