package de.kreiscraft.modules.shatteredcrown;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class DragonDebugCommand {
    static final String PERMISSION = "kreiscraft.shatteredcrown.debug";

    private DragonDebugCommand() {}

    static LiteralCommandNode<CommandSourceStack> build(Supplier<EncounterController> encounter,
            MessageService messages, Server server) {
        var root = Commands.literal("dragondebug")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(ctx -> control("status", ctx.getSource().getSender(), encounter, messages));
        for (String action : new String[] {"status", "pause", "resume", "cancel"}) {
            root.then(Commands.literal(action)
                    .executes(ctx -> control(action, ctx.getSource().getSender(), encounter, messages)));
        }
        var trigger = Commands.literal("trigger");
        for (var attack : EncounterController.DebugAttack.values()) {
            trigger.then(Commands.literal(attack.name().toLowerCase(Locale.ROOT))
                    .executes(ctx -> {
                        var sender = ctx.getSource().getSender();
                        if (!(sender instanceof Player player)) {
                            messages.send(sender, "shattered-crown.debug-target-required");
                            return 0;
                        }
                        return trigger(attack, sender, player, encounter, messages);
                    })
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SuggestionProviders.onlinePlayers(server, PERMISSION))
                            .executes(ctx -> {
                                var sender = ctx.getSource().getSender();
                                String name = StringArgumentType.getString(ctx, "player");
                                Player target = server.getPlayerExact(name);
                                if (target == null) {
                                    messages.send(sender, "shattered-crown.debug-target-missing", "player", name);
                                    return 0;
                                }
                                return trigger(attack, sender, target, encounter, messages);
                            })));
        }
        return root.then(trigger).build();
    }

    private static int control(String action, CommandSender sender, Supplier<EncounterController> supplier,
            MessageService messages) {
        var encounter = supplier.get();
        if (encounter == null) {
            messages.send(sender, "shattered-crown.debug-disabled");
            return 0;
        }
        switch (action) {
            case "pause" -> encounter.debugPause();
            case "resume" -> encounter.debugResume();
            case "cancel" -> encounter.debugCancel();
            default -> {
            }
        }
        encounter.debugStatus(sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int trigger(EncounterController.DebugAttack attack, CommandSender sender, Player target,
            Supplier<EncounterController> supplier, MessageService messages) {
        var encounter = supplier.get();
        if (encounter == null) {
            messages.send(sender, "shattered-crown.debug-disabled");
            return 0;
        }
        encounter.debugTrigger(attack, target, sender);
        return Command.SINGLE_SUCCESS;
    }
}
