package de.kreiscraft.modules.combat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.combat.CombatService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.time.Duration;
import java.util.List;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CombatCommand {

    private CombatCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            CombatService service, MessageService messages, Server server) {
        return Commands.literal("combat")
                .requires(src -> src.getSender().hasPermission("kreiscraft.combat.admin"))
                .then(Commands.argument("target", ArgumentTypes.players())
                        .suggests(
                                SuggestionProviders.onlinePlayers(
                                        server, "kreiscraft.combat.admin"))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> {
                                    List<Player> targets = ctx.getArgument(
                                            "target", PlayerSelectorArgumentResolver.class)
                                            .resolve(ctx.getSource());
                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                    Duration duration = Duration.ofSeconds(seconds);
                                    CommandSender sender = ctx.getSource().getSender();
                                    for (Player target : targets) {
                                        service.tag(target.getUniqueId(), duration);
                                        messages.send(
                                                sender,
                                                "combat.manual-tag",
                                                "player", target.getName(),
                                                "seconds", String.valueOf(seconds));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
