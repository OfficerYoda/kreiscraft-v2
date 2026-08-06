package de.kreiscraft.modules.startsequence.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.startsequence.StartSequenceService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class StartCommand {

    private StartCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            StartSequenceService service, MessageService messages) {
        return Commands.literal("start")
                .requires(src -> src.getSender() instanceof Player
                        && src.getSender().hasPermission("kreiscraft.startsequence.admin"))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            if (!service.hasStarted()) {
                                messages.send(
                                        ctx.getSource().getSender(),
                                        "start-sequence.not-started");
                                return Command.SINGLE_SUCCESS;
                            }
                            service.reset();
                            messages.send(
                                    ctx.getSource().getSender(), "start-sequence.reset");
                            return Command.SINGLE_SUCCESS;
                        }))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    if (service.hasStarted()) {
                        messages.send(player, "start-sequence.already-started");
                        return Command.SINGLE_SUCCESS;
                    }
                    World world = player.getWorld();
                    service.trigger(world);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
