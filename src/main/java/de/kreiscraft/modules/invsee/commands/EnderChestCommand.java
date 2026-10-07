package de.kreiscraft.modules.invsee.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.invsee.InvseeGui;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * {@code /enderchest <player>} (aliases: {@code ec}, {@code echest}) — shows the target's ender
 * chest.
 *
 * <ul>
 *   <li>Viewer with {@code kreiscraft.invsee.edit}: editable 3-row view.
 *   <li>Without edit perm: read-only 3-row view.
 * </ul>
 *
 * <p>Offline targets are not supported (ender-chest data is not included in offline NBT for the
 * same player-data file as the main inventory).
 */
@SuppressWarnings("UnstableApiUsage")
public final class EnderChestCommand {

    private EnderChestCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            Gui gui, MessageService messages) {
        return Commands.literal("enderchest")
                .requires(src -> src.getSender().hasPermission("kreiscraft.invsee.view"))
                .then(
                        Commands.argument("target", StringArgumentType.word())
                                .executes(
                                        ctx -> {
                                            if (!(ctx.getSource().getSender() instanceof Player viewer)) {
                                                messages.send(
                                                        ctx.getSource().getSender(),
                                                        "core.player-only");
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String name =
                                                    StringArgumentType.getString(ctx, "target");
                                            Player target = Bukkit.getPlayer(name);
                                            if (target == null) {
                                                messages.send(
                                                        viewer,
                                                        "invsee.target-not-found",
                                                        "player",
                                                        name);
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            openEnderChest(viewer, target, gui, messages);
                                            return Command.SINGLE_SUCCESS;
                                        }))
                .build();
    }

    private static void openEnderChest(
            Player viewer, Player target, Gui gui, MessageService messages) {
        xyz.xenondevs.invui.gui.Gui invuiGui;
        if (viewer.hasPermission("kreiscraft.invsee.edit")) {
            invuiGui = InvseeGui.buildEnderChestEditable(target.getEnderChest());
        } else {
            invuiGui = InvseeGui.buildEnderChestReadOnly(target.getEnderChest());
        }
        Component title = messages.renderFor(viewer, "invsee.gui-title-ec", "player", target.getName());
        gui.open(viewer, invuiGui, title);
    }
}
