package de.kreiscraft.modules.combat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.combat.CombatService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.time.Duration;
import org.bukkit.entity.Player;

public final class CombatCommand {

    private CombatCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            CombatService service, MessageService messages) {
        return Commands.literal("combat")
                .requires(src -> src.getSender().hasPermission("kreiscraft.combat.admin"))
                .then(Commands.argument("target", ArgumentTypes.player())
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> {
                                    Player target = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                            .resolve(ctx.getSource())
                                            .getFirst();
                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                    service.tag(target.getUniqueId(), Duration.ofSeconds(seconds));
                                    messages.send(
                                            ctx.getSource().getSender(),
                                            "combat.manual-tag",
                                            "player", target.getName(),
                                            "seconds", String.valueOf(seconds));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
