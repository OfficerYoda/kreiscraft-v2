package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class KreiscraftGamemodeCommand {

    private KreiscraftGamemodeCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Server server) {
        return Commands.literal("kreiscraftgamemode")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.gamemode"))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                messages.send(
                                        sender,
                                        "admin.usage",
                                        "usage",
                                        "/kreiscraftgamemode <mode> [player]");
                                return Command.SINGLE_SUCCESS;
                            }
                            String mode = StringArgumentType.getString(ctx, "mode");
                            GameMode gm = resolveGameMode(mode);
                            if (gm == null) {
                                messages.send(
                                        sender,
                                        "admin.usage",
                                        "usage",
                                        "/kreiscraftgamemode <mode> [player]");
                                return Command.SINGLE_SUCCESS;
                            }
                            player.setGameMode(gm);
                            messages.send(
                                    sender,
                                    "admin.gamemode-set",
                                    "player",
                                    player.getName(),
                                    "mode",
                                    gm.name().toLowerCase());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(
                                        SuggestionProviders.onlinePlayers(
                                                server, "kreiscraft.admin.gamemode"))
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String mode = StringArgumentType.getString(ctx, "mode");
                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    GameMode gm = resolveGameMode(mode);
                                    if (gm == null) {
                                        messages.send(
                                                sender,
                                                "admin.usage",
                                                "usage",
                                                "/kreiscraftgamemode <mode> [player]");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Player target = server.getPlayer(targetName);
                                    if (target == null) {
                                        messages.send(
                                                sender,
                                                "admin.target-not-found",
                                                "player",
                                                targetName);
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    target.setGameMode(gm);
                                    messages.send(
                                            sender,
                                            "admin.gamemode-set",
                                            "player",
                                            target.getName(),
                                            "mode",
                                            gm.name().toLowerCase());
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private static GameMode resolveGameMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "0", "survival" -> GameMode.SURVIVAL;
            case "1", "creative" -> GameMode.CREATIVE;
            case "2", "adventure" -> GameMode.ADVENTURE;
            case "3", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }
}
