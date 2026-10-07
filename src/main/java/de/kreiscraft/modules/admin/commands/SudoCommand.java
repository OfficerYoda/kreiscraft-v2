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

public final class SudoCommand {

    private SudoCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Server server) {
        return Commands.literal("sudo")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.sudo"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(
                                SuggestionProviders.allAndOnlinePlayers(
                                        server, "kreiscraft.admin.sudo"))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String targetArg =
                                            StringArgumentType.getString(ctx, "target");
                                    String text = StringArgumentType.getString(ctx, "text");
                                    if (targetArg.equalsIgnoreCase("all")) {
                                        for (Player p : server.getOnlinePlayers()) {
                                            p.chat(text);
                                        }
                                        messages.send(
                                                sender,
                                                "admin.sudo-executed",
                                                "player",
                                                "all",
                                                "text",
                                                text);
                                    } else {
                                        Player target = server.getPlayer(targetArg);
                                        if (target == null) {
                                            messages.send(
                                                    sender,
                                                    "admin.target-not-found",
                                                    "player",
                                                    targetArg);
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        target.chat(text);
                                        messages.send(
                                                sender,
                                                "admin.sudo-executed",
                                                "player",
                                                target.getName(),
                                                "text",
                                                text);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
