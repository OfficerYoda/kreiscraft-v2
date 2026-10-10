package de.kreiscraft.modules.jail.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.jail.JailEntry;
import de.kreiscraft.modules.jail.JailService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /jail <target> [reason...]} — jails an unjailed player; if the target is already jailed,
 * releases them instead. The {@code /kerker} alias is built via {@link #buildWithName}.
 *
 * <p>The jail location is read from the {@link JailService} (operator-owned Server Configuration);
 * a freshly jailed player is teleported to it and receives a localized direct message whose reason
 * is styled distinctly from the surrounding prose. When no reason is supplied, both the sender and
 * the target see the localized {@code jail.no-reason} placeholder instead of an empty string.
 */
public final class JailCommand {

    private JailCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            JailService service, MessageService messages, Server server) {
        return buildWithName("jail", service, messages, server);
    }

    /**
     * Builds the command node under the given {@code name} (use {@code "jail"} for the canonical
     * command and {@code "kerker"} for the alias). Both roots share identical behavior and
     * permissions.
     */
    public static LiteralCommandNode<CommandSourceStack> buildWithName(
            String name, JailService service, MessageService messages, Server server) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission("kreiscraft.jail.use"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(SuggestionProviders.onlinePlayers(server, "kreiscraft.jail.use"))
                        .executes(ctx -> execute(ctx, service, messages, ""))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(
                                        ctx -> execute(
                                                ctx,
                                                service,
                                                messages,
                                                StringArgumentType.getString(ctx, "reason")))))
                .build();
    }

    private static int execute(
            CommandContext<CommandSourceStack> ctx,
            JailService service,
            MessageService messages,
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
            service.jailLocation().ifPresent(target::teleport);
            messages.send(
                    sender,
                    "jail.jailed",
                    "player",
                    target.getName(),
                    "reason",
                    reasonPlaceholder(messages, sender, reason));
            messages.send(
                    target,
                    "jail.jailed-target",
                    "reason",
                    reasonPlaceholder(messages, target, reason));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The value for the {@code <reason>} placeholder: the supplied reason string, or — when none was
     * given — the recipient-localized {@code jail.no-reason}
     * {@link net.kyori.adventure.text.Component}, rendered per recipient so each party sees the
     * fallback in their own language.
     */
    private static Object reasonPlaceholder(
            MessageService messages, CommandSender recipient, String reason) {
        if (reason.isBlank()) {
            return messages.renderFor(recipient, "jail.no-reason");
        }
        return reason;
    }
}
