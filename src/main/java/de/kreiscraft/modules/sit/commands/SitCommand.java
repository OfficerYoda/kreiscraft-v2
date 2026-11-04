package de.kreiscraft.modules.sit.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.sit.SitService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public final class SitCommand {

    public static final String PERMISSION = "kreiscraft.sit.use";

    private SitCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(SitService service, MessageService messages) {
        return Commands.literal("sit")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player player) {
                        service.toggle(player);
                    } else {
                        messages.send(ctx.getSource().getSender(), "core.player-only");
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
