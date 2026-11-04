package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.modules.admin.TpsBar;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TpsCommand {

    private TpsCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, PlaceholderResolver resolver, TpsBar bar) {
        return Commands.literal("tps")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.tps"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    Player playerCtx = sender instanceof Player p ? p : null;
                    String tps = resolver.resolveString("%tps%", playerCtx);
                    messages.send(sender, "admin.tps", "tps", tps);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("bar")
                        .requires(src -> src.getSender() instanceof Player
                                && src.getSender().hasPermission("kreiscraft.admin.tps"))
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            if (bar.toggle(player)) {
                                messages.send(player, "admin.tps-bar.enabled");
                            } else {
                                messages.send(player, "admin.tps-bar.disabled");
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
