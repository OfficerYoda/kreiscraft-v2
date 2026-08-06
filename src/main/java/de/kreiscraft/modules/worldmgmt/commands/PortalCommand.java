package de.kreiscraft.modules.worldmgmt.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class PortalCommand {

    /** Represents the target dimension for a portal conversion. */
    enum PortalDirection {
        NETHER, OVERWORLD
    }

    private PortalCommand() {}

    /**
     * Converts a coordinate from the source dimension to the target dimension.
     * NETHER means the target is the nether (divide by 8). OVERWORLD means the
     * target is the overworld (multiply by 8).
     */
    static int convert(int coord, PortalDirection dir) {
        return switch (dir) {
            case NETHER -> coord / 8;
            case OVERWORLD -> coord * 8;
        };
    }

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, PaperPlatform platform) {
        return Commands.literal("portal")
                .requires(src -> src.getSender().hasPermission("kreiscraft.worldmgmt.portal"))
                .then(Commands.literal("nether")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            return executePortal(
                                                    player,
                                                    PortalDirection.NETHER,
                                                    IntegerArgumentType.getInteger(ctx, "x"),
                                                    IntegerArgumentType.getInteger(ctx, "z"),
                                                    platform,
                                                    messages);
                                        })))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            return executePortal(
                                    player,
                                    PortalDirection.NETHER,
                                    player.getLocation().getBlockX(),
                                    player.getLocation().getBlockZ(),
                                    platform,
                                    messages);
                        }))
                .then(Commands.literal("overworld")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            return executePortal(
                                                    player,
                                                    PortalDirection.OVERWORLD,
                                                    IntegerArgumentType.getInteger(ctx, "x"),
                                                    IntegerArgumentType.getInteger(ctx, "z"),
                                                    platform,
                                                    messages);
                                        })))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            return executePortal(
                                    player,
                                    PortalDirection.OVERWORLD,
                                    player.getLocation().getBlockX(),
                                    player.getLocation().getBlockZ(),
                                    platform,
                                    messages);
                        }))
                .build();
    }

    static int executePortal(
            Player player,
            PortalDirection dir,
            int srcX,
            int srcZ,
            PaperPlatform platform,
            MessageService messages) {
        int targetX = convert(srcX, dir);
        int targetZ = convert(srcZ, dir);

        World.Environment targetEnv =
                dir == PortalDirection.NETHER ? World.Environment.NETHER : World.Environment.NORMAL;

        Optional<World> targetWorld =
                platform.server().getWorlds().stream()
                        .filter(w -> w.getEnvironment() == targetEnv)
                        .findFirst();

        if (targetWorld.isEmpty()) {
            messages.send(player, "world-mgmt.portal-no-target");
            return Command.SINGLE_SUCCESS;
        }

        World world = targetWorld.get();
        int targetY = world.getSpawnLocation().getBlockY();
        messages.send(
                player,
                "world-mgmt.portal-converted",
                "x", targetX,
                "y", targetY,
                "z", targetZ);
        player.teleport(new Location(world, targetX, targetY, targetZ));
        return Command.SINGLE_SUCCESS;
    }
}
