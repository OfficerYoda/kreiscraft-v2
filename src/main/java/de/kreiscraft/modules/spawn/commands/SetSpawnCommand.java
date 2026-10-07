package de.kreiscraft.modules.spawn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.spawn.SpawnService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * Sets the server spawn point to the executing player's current location and stores the given
 * teleport radius. Both the location and the radius are persisted to {@code data/spawn.yml}.
 */
public final class SetSpawnCommand {

    private SetSpawnCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            SpawnService service, MessageService messages) {
        return Commands.literal("setspawn")
                .requires(src -> src.getSender().hasPermission("kreiscraft.spawn.set"))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            int radius = IntegerArgumentType.getInteger(ctx, "radius");
                            service.setSpawn(player.getLocation(), radius);
                            messages.send(player, "spawn.spawn-set");
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
