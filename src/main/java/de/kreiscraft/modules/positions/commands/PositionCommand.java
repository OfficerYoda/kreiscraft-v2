package de.kreiscraft.modules.positions.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.positions.PositionEntry;
import de.kreiscraft.modules.positions.PositionsConfig;
import de.kreiscraft.modules.positions.PositionsGui;
import de.kreiscraft.modules.positions.PositionsService;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Brigadier command tree for {@code /positions} ({@code /pos} alias).
 *
 * <pre>
 * /positions                       – opens the positions GUI
 * /positions set &lt;name&gt;           – saves current location as &lt;name&gt;
 * /positions get &lt;name&gt;           – prints coords for &lt;name&gt;
 * /positions delete &lt;name&gt;        – removes &lt;name&gt;
 * /positions list                  – lists all saved positions
 * </pre>
 *
 * <p>All subcommands require the {@code kreiscraft.positions.use} permission.
 */
public final class PositionCommand {

    private PositionCommand() {}

    /**
     * Builds the {@code /positions} command node.
     *
     * @param service        the positions service
     * @param messages       message service for user feedback
     * @param gui            shared GUI facade (may be {@code null} when called
     *                       from tests that do not exercise the GUI path)
     * @param configSupplier supplier for the current config (used for
     *                       {@code teleportOnClick} and {@code guiRows})
     */
    @SuppressWarnings("NullAway") // gui parameter is optional for non-GUI subcommands
    public static LiteralCommandNode<CommandSourceStack> build(
            PositionsService service,
            MessageService messages,
            Gui gui,
            Supplier<PositionsConfig> configSupplier) {

        return build("positions", service, messages, gui, configSupplier);
    }

    static LiteralCommandNode<CommandSourceStack> build(
            String commandName,
            PositionsService service,
            MessageService messages,
            Gui gui,
            Supplier<PositionsConfig> configSupplier) {

        return Commands.literal(commandName)
                .requires(src -> src.getSender().hasPermission("kreiscraft.positions.use"))
                // /positions — opens GUI
                .executes(
                        ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                // /pos with no args is GUI-only; ignore non-player senders
                                return Command.SINGLE_SUCCESS;
                            }
                            PositionsConfig cfg = configSupplier.get();
                            Map<String, PositionEntry> positions =
                                    service.forPlayer(player.getUniqueId());
                            xyz.xenondevs.invui.gui.Gui invuiGui =
                                    PositionsGui.build(
                                            positions,
                                            cfg,
                                            messages,
                                            (p, entry) -> p.teleport(entry.location()));
                            gui.open(
                                    player,
                                    invuiGui,
                                    messages.renderFor(player, "positions.gui-title"));
                            return Command.SINGLE_SUCCESS;
                        })
                // /positions set <name>
                .then(
                        Commands.literal("set")
                                .then(
                                        Commands.argument("name", StringArgumentType.word())
                                                .executes(
                                                        ctx -> {
                                                            CommandSender sender =
                                                                    ctx.getSource().getSender();
                                                            if (!(sender instanceof Player player)) {
                                                                return Command.SINGLE_SUCCESS;
                                                            }
                                                            String name =
                                                                    StringArgumentType.getString(
                                                                            ctx, "name");
                                                            service.set(
                                                                    player.getUniqueId(),
                                                                    name,
                                                                    player.getLocation(),
                                                                    player.getUniqueId(),
                                                                    player.getName());
                                                            messages.send(
                                                                    sender,
                                                                    "positions.set",
                                                                    "name",
                                                                    name);
                                                            return Command.SINGLE_SUCCESS;
                                                        })))
                // /positions get <name>
                .then(
                        Commands.literal("get")
                                .then(
                                        Commands.argument("name", StringArgumentType.word())
                                                .executes(
                                                        ctx -> {
                                                            CommandSender sender =
                                                                    ctx.getSource().getSender();
                                                            if (!(sender instanceof Player player)) {
                                                                return Command.SINGLE_SUCCESS;
                                                            }
                                                            String name =
                                                                    StringArgumentType.getString(
                                                                            ctx, "name");
                                                            Optional<PositionEntry> entry =
                                                                    service.get(
                                                                            player.getUniqueId(),
                                                                            name);
                                                            if (entry.isEmpty()) {
                                                                messages.send(
                                                                        sender,
                                                                        "positions.not-found",
                                                                        "name",
                                                                        name);
                                                            } else {
                                                                org.bukkit.Location loc =
                                                                        entry.get().location();
                                                                String world =
                                                                        loc.getWorld() != null
                                                                                ? loc.getWorld()
                                                                                        .getName()
                                                                                : "?";
                                                                messages.send(
                                                                        sender,
                                                                        "positions.list-entry",
                                                                        "name",
                                                                        name,
                                                                        "world",
                                                                        world,
                                                                        "x",
                                                                        String.valueOf(
                                                                                (int) loc
                                                                                        .getX()),
                                                                        "y",
                                                                        String.valueOf(
                                                                                (int) loc
                                                                                        .getY()),
                                                                        "z",
                                                                        String.valueOf(
                                                                                (int) loc
                                                                                        .getZ()));
                                                            }
                                                            return Command.SINGLE_SUCCESS;
                                                        })))
                // /positions delete <name>
                .then(
                        Commands.literal("delete")
                                .then(
                                        Commands.argument("name", StringArgumentType.word())
                                                .executes(
                                                        ctx -> {
                                                            CommandSender sender =
                                                                    ctx.getSource().getSender();
                                                            if (!(sender instanceof Player player)) {
                                                                return Command.SINGLE_SUCCESS;
                                                            }
                                                            String name =
                                                                    StringArgumentType.getString(
                                                                            ctx, "name");
                                                            Optional<PositionEntry> existing =
                                                                    service.get(
                                                                            player.getUniqueId(),
                                                                            name);
                                                            if (existing.isEmpty()) {
                                                                messages.send(
                                                                        sender,
                                                                        "positions.not-found",
                                                                        "name",
                                                                        name);
                                                            } else {
                                                                service.remove(
                                                                        player.getUniqueId(),
                                                                        name);
                                                                messages.send(
                                                                        sender,
                                                                        "positions.removed",
                                                                        "name",
                                                                        name);
                                                            }
                                                            return Command.SINGLE_SUCCESS;
                                                        })))
                // /positions list
                .then(
                        Commands.literal("list")
                                .executes(
                                        ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) {
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Map<String, PositionEntry> positions =
                                                    service.forPlayer(player.getUniqueId());
                                            messages.send(
                                                    sender,
                                                    "positions.list-header",
                                                    "count",
                                                    String.valueOf(positions.size()));
                                            for (var e : positions.entrySet()) {
                                                org.bukkit.Location loc = e.getValue().location();
                                                String world =
                                                        loc.getWorld() != null
                                                                ? loc.getWorld().getName()
                                                                : "?";
                                                messages.send(
                                                        sender,
                                                        "positions.list-entry",
                                                        "name",
                                                        e.getKey(),
                                                        "world",
                                                        world,
                                                        "x",
                                                        String.valueOf((int) loc.getX()),
                                                        "y",
                                                        String.valueOf((int) loc.getY()),
                                                        "z",
                                                        String.valueOf((int) loc.getZ()));
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        }))
                .build();
    }
}
