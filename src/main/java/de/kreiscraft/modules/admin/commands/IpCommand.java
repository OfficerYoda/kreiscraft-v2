package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.net.InetSocketAddress;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class IpCommand {

    private IpCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Server server) {
        return Commands.literal("ip")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.ip"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (sender instanceof Player player) {
                        InetSocketAddress addr = player.getAddress();
                        String ip = addr != null ? addr.getAddress().getHostAddress() : "unknown";
                        messages.send(sender, "admin.ip", "player", player.getName(), "ip", ip);
                    } else {
                        messages.send(sender, "admin.ip", "player", "console", "ip", "local");
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
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
                            InetSocketAddress addr = target.getAddress();
                            String ip =
                                    addr != null
                                            ? addr.getAddress().getHostAddress()
                                            : "unknown";
                            messages.send(
                                    sender, "admin.ip", "player", target.getName(), "ip", ip);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
