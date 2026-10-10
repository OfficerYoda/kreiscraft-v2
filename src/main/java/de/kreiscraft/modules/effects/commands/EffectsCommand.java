package de.kreiscraft.modules.effects.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.effects.EffectsService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Handles {@code /effects} (report state and usage), {@code /effects toggle} (flip and persist), and
 * {@code /effects <particle>} (choose and persist a particle type).
 *
 * <p>The bare command never mutates state: it only reports the effective state and particle plus
 * the available usage, so a player cannot change their trail by accident. The particle argument's
 * completion reflects the player's effective preference with a marker tooltip.
 */
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
                            report(service, messages, (Player) ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                .then(
                        Commands.literal("toggle")
                                .executes(
                                        ctx -> {
                                            Player player =
                                                    (Player) ctx.getSource().getSender();
                                            service.toggle(player.getUniqueId());
                                            String key =
                                                    service.isEnabled(player.getUniqueId())
                                                            ? "effects.enabled"
                                                            : "effects.disabled";
                                            messages.send(player, key);
                                            return Command.SINGLE_SUCCESS;
                                        }))
                .then(
                        Commands.argument("particle", StringArgumentType.word())
                                .suggests(
                                        SuggestionProviders.particles(
                                                "kreiscraft.effects.use",
                                                src -> src.getSender() instanceof Player player
                                                        ? service.getParticle(
                                                                player.getUniqueId())
                                                        : null,
                                                src -> messages.renderFor(
                                                        src.getSender(),
                                                        "effects.completion-current")))
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

    private static void report(EffectsService service, MessageService messages, Player player) {
        UUID id = player.getUniqueId();
        Component state =
                messages.renderFor(
                        player, service.isEnabled(id) ? "effects.state-on" : "effects.state-off");
        messages.send(
                player,
                "effects.status",
                "state",
                state,
                "particle",
                service.getParticle(id).name());
        messages.send(player, "effects.usage");
    }
}
