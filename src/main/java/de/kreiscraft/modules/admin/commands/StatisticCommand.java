package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StatisticCommand {

    private StatisticCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Server server) {
        return Commands.literal("statistic")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.statistic"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String playerName =
                                            StringArgumentType.getString(ctx, "player");
                                    String statName = StringArgumentType.getString(ctx, "stat");
                                    Player target = server.getPlayer(playerName);
                                    if (target == null) {
                                        messages.send(
                                                sender,
                                                "admin.target-not-found",
                                                "player",
                                                playerName);
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Statistic stat;
                                    try {
                                        stat = Statistic.valueOf(statName.toUpperCase());
                                    } catch (IllegalArgumentException e) {
                                        messages.send(
                                                sender,
                                                "admin.usage",
                                                "usage",
                                                "/statistic <player> <stat>");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    int value = target.getStatistic(stat);
                                    messages.send(
                                            sender,
                                            "admin.statistic",
                                            "player",
                                            target.getName(),
                                            "stat",
                                            stat.name().toLowerCase(),
                                            "value",
                                            value);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
