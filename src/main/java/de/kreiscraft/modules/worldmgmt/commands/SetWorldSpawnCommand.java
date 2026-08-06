package de.kreiscraft.modules.worldmgmt.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public final class SetWorldSpawnCommand {

    private SetWorldSpawnCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(MessageService messages) {
        return Commands.literal("setworldspawn")
                .requires(
                        src -> src.getSender().hasPermission("kreiscraft.worldmgmt.setworldspawn"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    return setWorldSpawn(player, messages);
                })
                .build();
    }

    static int setWorldSpawn(Player player, MessageService messages) {
        player.getWorld().setSpawnLocation(player.getLocation());
        messages.send(player, "world-mgmt.spawn-set");
        return Command.SINGLE_SUCCESS;
    }
}
