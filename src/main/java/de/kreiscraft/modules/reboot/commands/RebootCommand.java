package de.kreiscraft.modules.reboot.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.reboot.RebootConfig;
import de.kreiscraft.modules.reboot.RebootService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Duration;

public final class RebootCommand {

    private RebootCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            RebootService service, MessageService messages, RebootConfig config) {
        return Commands.literal("reboot")
                .requires(src -> src.getSender().hasPermission("kreiscraft.reboot.admin"))
                .executes(ctx -> {
                    service.start(config.defaultCountdown(), "");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("cancel")
                        .executes(ctx -> {
                            if (!service.isRunning()) {
                                messages.send(ctx.getSource().getSender(), "reboot.not-running");
                                return Command.SINGLE_SUCCESS;
                            }
                            service.cancel();
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                            service.start(Duration.ofSeconds(seconds), "");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                    String reason = StringArgumentType.getString(ctx, "reason");
                                    service.start(Duration.ofSeconds(seconds), reason);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
