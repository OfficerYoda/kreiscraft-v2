package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class SpawnMobCommand {

    private SpawnMobCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(MessageService messages) {
        return Commands.literal("spawnmob")
                .requires(
                        src -> src.getSender() instanceof Player
                                && src.getSender()
                                        .hasPermission("kreiscraft.admin.spawnmob"))
                .then(Commands.argument("entityType", StringArgumentType.word())
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            String typeName = StringArgumentType.getString(ctx, "entityType");
                            EntityType type = resolveEntityType(typeName);
                            if (type == null) {
                                messages.send(
                                        player,
                                        "admin.usage",
                                        "usage",
                                        "/spawnmob <entityType> [count]");
                                return Command.SINGLE_SUCCESS;
                            }
                            player.getLocation().getWorld().spawnEntity(player.getLocation(), type);
                            messages.send(
                                    player,
                                    "admin.spawnmob-spawned",
                                    "count",
                                    1,
                                    "entity",
                                    type.name().toLowerCase());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> {
                                    Player player = (Player) ctx.getSource().getSender();
                                    String typeName =
                                            StringArgumentType.getString(ctx, "entityType");
                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    EntityType type = resolveEntityType(typeName);
                                    if (type == null) {
                                        messages.send(
                                                player,
                                                "admin.usage",
                                                "usage",
                                                "/spawnmob <entityType> [count]");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    for (int i = 0; i < count; i++) {
                                        player.getLocation()
                                                .getWorld()
                                                .spawnEntity(player.getLocation(), type);
                                    }
                                    messages.send(
                                            player,
                                            "admin.spawnmob-spawned",
                                            "count",
                                            count,
                                            "entity",
                                            type.name().toLowerCase());
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private static EntityType resolveEntityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
