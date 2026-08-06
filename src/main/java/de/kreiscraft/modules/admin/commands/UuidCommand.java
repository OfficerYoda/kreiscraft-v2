package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class UuidCommand {

    private UuidCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Server server) {
        return Commands.literal("uuid")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.uuid"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (sender instanceof Player player) {
                        messages.send(
                                sender,
                                "admin.uuid",
                                "player",
                                player.getName(),
                                "uuid",
                                player.getUniqueId().toString());
                    } else {
                        messages.send(
                                sender,
                                "admin.uuid",
                                "player",
                                "console",
                                "uuid",
                                UUID.nameUUIDFromBytes("console".getBytes()).toString());
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String targetName = StringArgumentType.getString(ctx, "player");
                            @SuppressWarnings("deprecation")
                            UUID uuid = server.getOfflinePlayer(targetName).getUniqueId();
                            messages.send(
                                    sender,
                                    "admin.uuid",
                                    "player",
                                    targetName,
                                    "uuid",
                                    uuid.toString());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
