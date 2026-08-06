package de.kreiscraft.modules.glow.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.glow.GlowService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public final class GlowCommand {

    private GlowCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            GlowService service, MessageService messages) {
        return Commands.literal("glow")
                .requires(src -> src.getSender().hasPermission("kreiscraft.glow.use"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    service.toggle(player.getUniqueId());
                    String key =
                            service.isGlowing(player.getUniqueId()) ? "glow.enabled" : "glow.disabled";
                    messages.send(player, key);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
