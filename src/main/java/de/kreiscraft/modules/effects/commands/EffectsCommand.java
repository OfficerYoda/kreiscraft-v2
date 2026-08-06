package de.kreiscraft.modules.effects.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.effects.EffectsService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Handles {@code /effects} (toggle) and {@code /effects <particle>} (choose particle type). */
public final class EffectsCommand {

    private EffectsCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            EffectsService service, MessageService messages) {
        return Commands.literal("effects")
                .requires(
                        src -> src.getSender() instanceof Player
                                && src.getSender()
                                        .hasPermission("kreiscraft.effects.use"))
                .executes(
                        ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            service.toggle(player.getUniqueId());
                            String key =
                                    service.isEnabled(player.getUniqueId())
                                            ? "effects.enabled"
                                            : "effects.disabled";
                            messages.send(player, key);
                            return Command.SINGLE_SUCCESS;
                        })
                .then(
                        Commands.argument("particle", StringArgumentType.word())
                                .executes(
                                        ctx -> {
                                            Player player = (Player) ctx.getSource().getSender();
                                            String input =
                                                    ctx.getArgument("particle", String.class)
                                                            .toUpperCase();
                                            try {
                                                Particle particle = Particle.valueOf(input);
                                                service.setParticle(
                                                        player.getUniqueId(), particle);
                                                messages.send(
                                                        player,
                                                        "effects.particle-set",
                                                        "particle",
                                                        input);
                                            } catch (IllegalArgumentException e) {
                                                messages.send(
                                                        player,
                                                        "effects.unknown-particle",
                                                        "particle",
                                                        input);
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        }))
                .build();
    }
}
