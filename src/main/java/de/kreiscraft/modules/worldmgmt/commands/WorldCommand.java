package de.kreiscraft.modules.worldmgmt.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class WorldCommand {

    private WorldCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, PaperPlatform platform) {
        return Commands.literal("world")
                .requires(src -> src.getSender().hasPermission("kreiscraft.worldmgmt.world"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            platform.server().getWorlds().stream()
                                    .map(World::getName)
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            return teleportToWorld(player, name, platform, messages);
                        }))
                .build();
    }

    static int teleportToWorld(
            Player player, String worldName, PaperPlatform platform, MessageService messages) {
        World world = platform.server().getWorld(worldName);
        if (world == null) {
            messages.send(player, "world-mgmt.unknown-world", "world", worldName);
            return Command.SINGLE_SUCCESS;
        }
        player.teleport(world.getSpawnLocation());
        messages.send(player, "world-mgmt.teleported", "world", worldName);
        return Command.SINGLE_SUCCESS;
    }
}
