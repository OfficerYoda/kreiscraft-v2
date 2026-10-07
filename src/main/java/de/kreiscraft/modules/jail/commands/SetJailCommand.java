package de.kreiscraft.modules.jail.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.jail.JailService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * {@code /setjail} — stores the executing player's current location as the operator-owned jail
 * location in Server Configuration ({@code modules.jail.location} in {@code config.yml}).
 *
 * <p>The write goes through the validated, atomic Server Configuration edit boundary, so a
 * rejected or failed edit cannot leave the running server and the durable file disagreeing.
 */
public final class SetJailCommand {

    private SetJailCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            JailService service, MessageService messages) {
        return Commands.literal("setjail")
                .requires(src -> src.getSender().hasPermission("kreiscraft.jail.set"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        messages.send(ctx.getSource().getSender(), "core.player-only");
                        return Command.SINGLE_SUCCESS;
                    }
                    service.setJailLocation(player.getLocation());
                    messages.send(player, "jail.set");
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
