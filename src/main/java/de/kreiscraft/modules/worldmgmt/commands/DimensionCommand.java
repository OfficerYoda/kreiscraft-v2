package de.kreiscraft.modules.worldmgmt.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.worldmgmt.Dimension;
import de.kreiscraft.modules.worldmgmt.DimensionAccessService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * Reports and toggles portal access to the Nether and End.
 *
 * <pre>
 * /dimension                      status of both dimensions
 * /dimension status               status of both dimensions
 * /dimension nether               Nether status
 * /dimension nether enable|disable
 * /dimension end                  End status
 * /dimension end enable|disable
 * </pre>
 */
public final class DimensionCommand {

    /** Permission required to inspect or change dimension access. */
    public static final String PERMISSION = "kreiscraft.worldmgmt.dimension";

    private DimensionCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            DimensionAccessService dimensions, MessageService messages) {
        return Commands.literal("dimension")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> statusAll(ctx.getSource().getSender(), dimensions, messages))
                .then(Commands.literal("status")
                        .executes(ctx -> statusAll(
                                ctx.getSource().getSender(), dimensions, messages)))
                .then(dimensionNode(Dimension.NETHER, dimensions, messages))
                .then(dimensionNode(Dimension.END, dimensions, messages))
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> dimensionNode(
            Dimension dimension, DimensionAccessService dimensions, MessageService messages) {
        return Commands.literal(dimension.key())
                .executes(ctx -> status(
                        ctx.getSource().getSender(), dimension, dimensions, messages))
                .then(Commands.literal("enable")
                        .executes(ctx -> set(
                                ctx.getSource().getSender(),
                                dimension,
                                true,
                                dimensions,
                                messages)))
                .then(Commands.literal("disable")
                        .executes(ctx -> set(
                                ctx.getSource().getSender(),
                                dimension,
                                false,
                                dimensions,
                                messages)))
                .build();
    }

    private static int statusAll(
            CommandSender sender,
            DimensionAccessService dimensions,
            MessageService messages) {
        status(sender, Dimension.NETHER, dimensions, messages);
        return status(sender, Dimension.END, dimensions, messages);
    }

    /** Package-private for direct unit tests without constructing a Brigadier context. */
    static int status(
            CommandSender sender,
            Dimension dimension,
            DimensionAccessService dimensions,
            MessageService messages) {
        Component state = messages.renderFor(
                sender,
                dimensions.isAllowed(dimension)
                        ? "world-mgmt.dimension-state-enabled"
                        : "world-mgmt.dimension-state-disabled");
        messages.send(
                sender,
                "world-mgmt.dimension-status",
                "dimension",
                dimension.displayName(),
                "state",
                state);
        return Command.SINGLE_SUCCESS;
    }

    /** Package-private for direct unit tests without constructing a Brigadier context. */
    static int set(
            CommandSender sender,
            Dimension dimension,
            boolean allowed,
            DimensionAccessService dimensions,
            MessageService messages) {
        if (!dimensions.setAllowed(dimension, allowed)) {
            messages.send(sender, "world-mgmt.dimension-update-failed");
            return Command.SINGLE_SUCCESS;
        }
        messages.send(
                sender,
                allowed
                        ? "world-mgmt.dimension-enabled"
                        : "world-mgmt.dimension-disabled",
                "dimension",
                dimension.displayName());
        return Command.SINGLE_SUCCESS;
    }
}
