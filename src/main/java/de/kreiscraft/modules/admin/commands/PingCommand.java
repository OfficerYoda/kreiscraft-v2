package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PingCommand {

    private PingCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Server server) {
        return Commands.literal("ping")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.ping"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (sender instanceof Player player) {
                        messages.send(
                                sender,
                                "admin.ping",
                                "player",
                                player.getName(),
                                "ping",
                                player.getPing());
                    } else {
                        messages.send(
                                sender,
                                "admin.usage",
                                "usage",
                                "/ping [player]");
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(SuggestionProviders.onlinePlayers(server, "kreiscraft.admin.ping"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String targetName = StringArgumentType.getString(ctx, "player");
                            Player target = server.getPlayer(targetName);
                            if (target == null) {
                                messages.send(
                                        sender,
                                        "admin.target-not-found",
                                        "player",
                                        targetName);
                                return Command.SINGLE_SUCCESS;
                            }
                            messages.send(
                                    sender,
                                    "admin.ping",
                                    "player",
                                    target.getName(),
                                    "ping",
                                    target.getPing());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
