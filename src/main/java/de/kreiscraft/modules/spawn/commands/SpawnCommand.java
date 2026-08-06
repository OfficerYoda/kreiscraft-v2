package de.kreiscraft.modules.spawn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.spawn.SpawnService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/** Teleports the executing player to the server spawn point. */
public final class SpawnCommand {

    private SpawnCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            SpawnService service, MessageService messages) {
        return Commands.literal("spawn")
                .requires(src -> src.getSender().hasPermission("kreiscraft.spawn.use"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        // Command may only be used by players.
                        return Command.SINGLE_SUCCESS;
                    }
                    if (service.spawnLocation().isEmpty()) {
                        messages.send(player, "spawn.no-spawn-set");
                        return Command.SINGLE_SUCCESS;
                    }
                    if (!service.isInSpawnRadius(player)) {
                        messages.send(player, "spawn.out-of-range");
                        return Command.SINGLE_SUCCESS;
                    }
                    player.teleport(service.spawnLocation().get());
                    messages.send(player, "spawn.teleported");
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
