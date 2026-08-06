package de.kreiscraft.modules.jail.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.jail.JailConfig;
import de.kreiscraft.modules.jail.JailService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /jail <target> [reason...]} — jails a player and teleports them to the configured jail
 * location. Refuses if the target has {@code kreiscraft.jail.bypass}.
 */
public final class JailCommand {

    private JailCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            JailService service, MessageService messages, AtomicReference<JailConfig> configRef) {
        return Commands.literal("jail")
                .requires(src -> src.getSender().hasPermission("kreiscraft.jail.use"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(ctx -> execute(ctx, service, messages, configRef, ""))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(
                                        ctx -> execute(
                                                ctx,
                                                service,
                                                messages,
                                                configRef,
                                                StringArgumentType.getString(
                                                        ctx, "reason")))))
                .build();
    }

    private static int execute(
            CommandContext<CommandSourceStack> ctx,
            JailService service,
            MessageService messages,
            AtomicReference<JailConfig> configRef,
            String reason) {
        CommandSender sender = ctx.getSource().getSender();
        String targetName = StringArgumentType.getString(ctx, "target");
        Player target = sender.getServer().getPlayer(targetName);

        if (target == null) {
            messages.send(sender, "jail.target-not-found", "player", targetName);
            return Command.SINGLE_SUCCESS;
        }
        if (target.hasPermission("kreiscraft.jail.bypass")) {
            messages.send(sender, "jail.target-has-bypass", "player", target.getName());
            return Command.SINGLE_SUCCESS;
        }
        if (service.isJailed(target.getUniqueId())) {
            messages.send(sender, "jail.already-jailed", "player", target.getName());
            return Command.SINGLE_SUCCESS;
        }

        Location restoreLocation = target.getLocation();
        service.jail(target.getUniqueId(), restoreLocation, reason);

        Location jailLocation = configRef.get().location();
        if (jailLocation != null) {
            target.teleport(jailLocation);
        }

        messages.send(sender, "jail.jailed", "player", target.getName(), "reason", reason);
        return Command.SINGLE_SUCCESS;
    }
}
