package de.kreiscraft.modules.jail.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.jail.JailEntry;
import de.kreiscraft.modules.jail.JailService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /unjail <target>} — releases a jailed player and teleports them back to their original
 * location.
 */
public final class UnjailCommand {

    private UnjailCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            JailService service, MessageService messages) {
        return Commands.literal("unjail")
                .requires(src -> src.getSender().hasPermission("kreiscraft.jail.use"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String targetName = StringArgumentType.getString(ctx, "target");
                            Player target = sender.getServer().getPlayer(targetName);

                            if (target == null) {
                                messages.send(
                                        sender, "jail.target-not-found", "player", targetName);
                                return Command.SINGLE_SUCCESS;
                            }

                            Optional<JailEntry> released = service.release(target.getUniqueId());
                            if (released.isEmpty()) {
                                messages.send(
                                        sender, "jail.not-jailed", "player", target.getName());
                                return Command.SINGLE_SUCCESS;
                            }

                            var restore = released.get().restoreLocation();
                            if (restore != null) {
                                target.teleport(restore);
                            }

                            messages.send(sender, "jail.released", "player", target.getName());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
