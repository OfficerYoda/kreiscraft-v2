package de.kreiscraft.modules.jail.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.jail.JailConfig;
import de.kreiscraft.modules.jail.JailEntry;
import de.kreiscraft.modules.jail.JailService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /jail <target> [reason...]} — jails an unjailed player; if the target is already jailed,
 * releases them instead. The {@code /kerker} alias is built via {@link #buildWithName}.
 */
public final class JailCommand {

    private JailCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            JailService service, MessageService messages, AtomicReference<JailConfig> configRef) {
        return buildWithName("jail", service, messages, configRef);
    }

    /**
     * Builds the command node under the given {@code name} (use {@code "jail"} for the canonical
     * command and {@code "kerker"} for the alias). Both roots share identical behavior and
     * permissions.
     */
    public static LiteralCommandNode<CommandSourceStack> buildWithName(
            String name,
            JailService service,
            MessageService messages,
            AtomicReference<JailConfig> configRef) {
        return Commands.literal(name)
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
                                                StringArgumentType.getString(ctx, "reason")))))
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

        if (service.isJailed(target.getUniqueId())) {
            // Release — bypass permission does not prevent releasing an already-jailed player.
            Optional<JailEntry> released = service.release(target.getUniqueId());
            released.ifPresent(entry -> {
                Location restore = entry.restoreLocation();
                if (restore != null) {
                    target.teleport(restore);
                }
            });
            messages.send(sender, "jail.released", "player", target.getName());
        } else {
            // Jail — refuse if the target has bypass permission.
            if (target.hasPermission("kreiscraft.jail.bypass")) {
                messages.send(sender, "jail.target-has-bypass", "player", target.getName());
                return Command.SINGLE_SUCCESS;
            }
            service.jail(target.getUniqueId(), target.getLocation(), reason);
            Location jailLocation = configRef.get().location();
            if (jailLocation != null) {
                target.teleport(jailLocation);
            }
            messages.send(sender, "jail.jailed", "player", target.getName(), "reason", reason);
        }
        return Command.SINGLE_SUCCESS;
    }
}
