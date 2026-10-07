package de.kreiscraft.modules.invsee.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
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
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * {@code /invsee <player>} — shows the target's inventory.
 *
 * <p>Viewing and editing share the single {@code kreiscraft.invsee.use} permission: a viewer who
 * passes the command's authorization may modify the target's live inventory directly. Offline
 * targets are shown as a read-only snapshot read through {@link OfflineInventoryReader}.
 *
 * <p>The target argument completes online players plus every known-offline profile through the
 * shared {@link SuggestionProviders#onlineAndKnownOfflineProfiles} provider.
 */
@SuppressWarnings("UnstableApiUsage")
public final class InvseeCommand {

    private InvseeCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            Gui gui, MessageService messages, OfflineInventoryReader offlineReader, Server server) {
        return Commands.literal("invsee")
                .requires(src -> src.getSender().hasPermission("kreiscraft.invsee.use"))
                .then(
                        Commands.argument("target", StringArgumentType.word())
                                .suggests(
                                        SuggestionProviders.onlineAndKnownOfflineProfiles(
                                                server, "kreiscraft.invsee.use"))
                                .executes(
                                        ctx -> {
                                            if (!(ctx.getSource().getSender() instanceof Player viewer)) {
                                                messages.send(ctx.getSource().getSender(), "core.player-only");
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String name =
                                                    StringArgumentType.getString(ctx, "target");
                                            Player target = Bukkit.getPlayer(name);
                                            if (target != null) {
                                                openOnlineInventory(viewer, target, gui, messages);
                                            } else {
                                                lookupAndOpenOffline(viewer, name, gui, messages, offlineReader);
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        }))
                .build();
    }

    // -----------------------------------------------------------------------
    // Package-private execution helpers (exposed for unit tests)
    // -----------------------------------------------------------------------

    static void openOnlineInventory(
            Player viewer, Player target, Gui gui, MessageService messages) {
        xyz.xenondevs.invui.gui.Gui invuiGui = InvseeGui.buildEditable(target.getInventory());
        Component title = messages.renderFor(viewer, "invsee.gui-title-inv", "player", target.getName());
        gui.open(viewer, invuiGui, title);
    }

    /** Resolves the offline player and delegates to {@link #openOfflineInventory}. */
    private static void lookupAndOpenOffline(
            Player viewer,
            String name,
            Gui gui,
            MessageService messages,
            OfflineInventoryReader offlineReader) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        openOfflineInventory(viewer, offline, name, gui, messages, offlineReader);
    }

    /**
     * Opens the offline inventory view.
     *
     * <p>Package-private so tests can inject a pre-built {@link OfflinePlayer} mock directly
     * without triggering {@code Bukkit.getOfflinePlayer} (which hits NMS state in tests).
     */
    static void openOfflineInventory(
            Player viewer,
            OfflinePlayer offline,
            String displayName,
            Gui gui,
            MessageService messages,
            OfflineInventoryReader offlineReader) {
        if (!offline.hasPlayedBefore()) {
            messages.send(viewer, "invsee.target-not-found", "player", displayName);
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
            messages.send(viewer, "invsee.offline-empty", "player", displayName);
            return;
        }
        xyz.xenondevs.invui.gui.Gui invuiGui = InvseeGui.buildOffline(items);
        Component title = messages.renderFor(viewer, "invsee.gui-title-inv", "player", displayName);
        gui.open(viewer, invuiGui, title);
    }
}
