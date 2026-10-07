package de.kreiscraft.modules.worldmgmt.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class PortalCommand {

    /** Target dimension for a portal coordinate conversion. */
    enum PortalDirection {
        NETHER, OVERWORLD
    }

    private PortalCommand() {}

    /**
     * Converts a coordinate from the source dimension to the target dimension.
     * NETHER divides by 8 (overworld → nether); OVERWORLD multiplies by 8 (nether → overworld).
     */
    static int convert(int coord, PortalDirection dir) {
        return switch (dir) {
            case NETHER -> coord / 8;
            case OVERWORLD -> coord * 8;
        };
    }

    public static LiteralCommandNode<CommandSourceStack> build(MessageService messages) {
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
                                                    messages);
                                        })))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            return executeImplicitPortal(player, PortalDirection.NETHER, messages);
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
                                                    messages);
                                        })))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            return executeImplicitPortal(player, PortalDirection.OVERWORLD, messages);
                        }))
                .build();
    }

    /**
     * Implicit conversion using the player's current location.
     * NETHER target requires the player to be in the NORMAL (overworld) environment.
     * OVERWORLD target requires the player to be in the NETHER environment.
     */
    static int executeImplicitPortal(Player player, PortalDirection dir, MessageService messages) {
        World.Environment required =
                dir == PortalDirection.NETHER ? World.Environment.NORMAL : World.Environment.NETHER;
        if (player.getWorld().getEnvironment() != required) {
            messages.send(
                    player,
                    dir == PortalDirection.NETHER
                            ? "world-mgmt.portal-not-overworld"
                            : "world-mgmt.portal-not-nether");
            return Command.SINGLE_SUCCESS;
        }
        int x = convert(player.getLocation().getBlockX(), dir);
        int z = convert(player.getLocation().getBlockZ(), dir);
        messages.send(player, "world-mgmt.portal-converted", "x", x, "z", z);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Explicit conversion using provided coordinates; no environment check.
     */
    static int executePortal(
            Player player, PortalDirection dir, int srcX, int srcZ, MessageService messages) {
        int targetX = convert(srcX, dir);
        int targetZ = convert(srcZ, dir);
        messages.send(player, "world-mgmt.portal-converted", "x", targetX, "z", targetZ);
        return Command.SINGLE_SUCCESS;
    }
}
