package de.kreiscraft.modules.glow.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.glow.GlowService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles {@code /glow} (self-service) and {@code /glow <player>} (moderation, requiring the
 * dedicated {@code kreiscraft.glow.other} permission).
 *
 * <p>When one player changes another player's glow, both parties receive a notification rendered in
 * their own persisted language: the actor is told what happened to the target, and the target is
 * told who changed them.
 */
public final class GlowCommand {

    private GlowCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            GlowService service, MessageService messages, Server server) {
        return Commands.literal("glow")
                .requires(src -> src.getSender().hasPermission("kreiscraft.glow.use"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    boolean enabled = service.toggle(player);
                    messages.send(player, enabled ? "glow.enabled" : "glow.disabled");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                        .requires(src -> src.getSender().hasPermission("kreiscraft.glow.other"))
                        .suggests(
                                SuggestionProviders.onlinePlayers(
                                        server, "kreiscraft.glow.other"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            List<Player> resolved = ctx.getArgument(
                                    "target", PlayerSelectorArgumentResolver.class)
                                    .resolve(ctx.getSource());
                            if (resolved.isEmpty()) {
                                return Command.SINGLE_SUCCESS;
                            }
                            Player target = resolved.getFirst();
                            boolean enabled = service.toggle(target);
                            messages.send(
                                    sender,
                                    enabled ? "glow.other-enabled" : "glow.other-disabled",
                                    "player",
                                    target.getName());
                            messages.send(
                                    target,
                                    enabled ? "glow.target-enabled" : "glow.target-disabled",
                                    "player",
                                    sender.getName());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
