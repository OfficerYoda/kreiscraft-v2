package de.kreiscraft.modules.spawn.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.spawn.SpawnService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Teleports the executing player to the server spawn point.
 *
 * <p>Guards: the player must be in the overworld ({@link World.Environment#NORMAL}) and within
 * the configured teleport radius. On success, 30 {@link Particle#CRIT} particles are spawned at
 * the player's location — matching the original RusticPrism behaviour.
 */
public final class SpawnCommand {

    private SpawnCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            SpawnService service, MessageService messages) {
        return Commands.literal("spawn")
                .requires(src -> src.getSender().hasPermission("kreiscraft.spawn.use"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    return execute(player, service, messages);
                })
                .build();
    }

    /** Package-private to allow direct unit-testing without constructing a Brigadier context. */
    static int execute(Player player, SpawnService service, MessageService messages) {
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            messages.send(player, "spawn.wrong-world");
            return Command.SINGLE_SUCCESS;
        }
        var spawn = service.spawnLocation();
        if (spawn.isEmpty()) {
            messages.send(player, "spawn.no-spawn-set");
            return Command.SINGLE_SUCCESS;
        }
        if (!service.isInSpawnRadius(player)) {
            messages.send(player, "spawn.out-of-range");
            return Command.SINGLE_SUCCESS;
        }
        // Capture departure point before teleporting — particles appear where the player was.
        Location from = player.getLocation();
        player.teleport(spawn.get());
        messages.send(player, "spawn.teleported");
        player.spawnParticle(Particle.CRIT, from, 30);
        return Command.SINGLE_SUCCESS;
    }
}
