package de.kreiscraft.modules.invsee.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.invsee.InvseeGui;
import de.kreiscraft.platform.OfflineInventoryReader;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * {@code /invsee <player>} — shows the target's inventory.
 *
 * <ul>
 *   <li>Viewer with {@code kreiscraft.invsee.edit}: editable 4-row (storage) view for online
 *       targets.
 *   <li>Viewer without edit perm: read-only 5-row view for online, 4-row for offline.
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class InvseeCommand {

    private InvseeCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            Gui gui, MessageService messages, OfflineInventoryReader offlineReader) {
        return Commands.literal("invsee")
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
                                            if (target != null) {
                                                openOnlineInventory(
                                                        viewer, target, gui, messages);
                                            } else {
                                                openOfflineInventory(
                                                        viewer,
                                                        name,
                                                        gui,
                                                        messages,
                                                        offlineReader);
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        }))
                .build();
    }

    private static void openOnlineInventory(
            Player viewer, Player target, Gui gui, MessageService messages) {
        xyz.xenondevs.invui.gui.Gui invuiGui;
        if (viewer.hasPermission("kreiscraft.invsee.edit")) {
            invuiGui = InvseeGui.buildEditable(target.getInventory());
        } else {
            invuiGui = InvseeGui.buildReadOnly(target.getInventory().getContents());
        }
        Component title = messages.render("invsee.gui-title-inv", "player", target.getName());
        gui.open(viewer, invuiGui, title);
    }

    @SuppressWarnings("deprecation")
    private static void openOfflineInventory(
            Player viewer,
            String name,
            Gui gui,
            MessageService messages,
            OfflineInventoryReader offlineReader) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.getUniqueId() == null || !offline.hasPlayedBefore()) {
            messages.send(viewer, "invsee.target-not-found", "player", name);
            return;
        }
        List<de.kreiscraft.platform.SlottedItem> items;
        try {
            items = offlineReader.readInventory(offline.getUniqueId());
        } catch (IOException e) {
            messages.send(viewer, "invsee.read-error");
            return;
        }
        if (items.isEmpty()) {
            messages.send(viewer, "invsee.offline-empty", "player", name);
            return;
        }
        xyz.xenondevs.invui.gui.Gui invuiGui = InvseeGui.buildOffline(items);
        Component title = messages.render("invsee.gui-title-inv", "player", name);
        gui.open(viewer, invuiGui, title);
    }
}
