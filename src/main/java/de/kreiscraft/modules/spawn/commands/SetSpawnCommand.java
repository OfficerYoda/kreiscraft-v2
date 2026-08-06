package de.kreiscraft.modules.spawn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.spawn.SpawnService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/** Sets the server spawn point to the executing player's current location. */
public final class SetSpawnCommand {

    private SetSpawnCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            SpawnService service, MessageService messages) {
        return Commands.literal("setspawn")
                .requires(src -> src.getSender().hasPermission("kreiscraft.spawn.set"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    service.setSpawn(player.getLocation());
                    messages.send(player, "spawn.spawn-set");
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
