package de.kreiscraft.modules.invsee.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.invsee.InvseeGui;
import de.kreiscraft.platform.OfflinePlayerDataEditor;
import de.kreiscraft.platform.OfflinePlayerEditSession;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.inventory.VirtualInventory;

/**
 * {@code /invsee <player>} — shows the target's inventory.
 *
 * <p>Viewing and editing share the single {@code kreiscraft.invsee.use} permission: a viewer who
 * passes the command's authorization may modify the target's live inventory directly. Offline
 * targets are opened as an editable snapshot through {@link OfflinePlayerDataEditor} and committed
 * through the two-phase offline-write boundary.
 *
 * <p>The target argument completes online players plus every known-offline profile through the
 * shared {@link SuggestionProviders#onlineAndKnownOfflineProfiles} provider.
 */
@SuppressWarnings("UnstableApiUsage")
public final class InvseeCommand {

    private InvseeCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            Gui gui,
            MessageService messages,
            OfflinePlayerDataEditor offlineEditor,
            Scheduler scheduler,
            Server server) {
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
                                                lookupAndOpenOffline(
                                                        viewer,
                                                        name,
                                                        gui,
                                                        messages,
                                                        offlineEditor,
                                                        scheduler,
                                                        server);
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
            OfflinePlayerDataEditor offlineEditor,
            Scheduler scheduler,
            Server server) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        openOfflineInventory(
                viewer, offline, name, gui, messages, offlineEditor, scheduler, server);
    }

    /**
     * Opens the editable offline inventory snapshot.
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
            OfflinePlayerDataEditor offlineEditor,
            Scheduler scheduler,
            Server server) {
        if (!offline.hasPlayedBefore()) {
            messages.send(viewer, "invsee.target-not-found", "player", displayName);
            return;
        }
        OfflinePlayerEditSession session;
        try {
            session = offlineEditor.openForEdit(offline.getUniqueId()).orElse(null);
        } catch (IOException e) {
            messages.send(viewer, "invsee.read-error");
            return;
        }
        if (session == null) {
            messages.send(viewer, "invsee.offline-empty", "player", displayName);
            return;
        }
        VirtualInventory contents = InvseeGui.offlineInventoryContents(session.snapshot());
        xyz.xenondevs.invui.gui.Gui invuiGui = InvseeGui.buildOfflineInventory(contents);
        Component title = messages.renderFor(viewer, "invsee.gui-title-inv", "player", displayName);
        gui.open(
                viewer,
                invuiGui,
                title,
                reason -> commitOfflineInventory(
                        viewer,
                        offline.getUniqueId(),
                        displayName,
                        contents,
                        session,
                        messages,
                        scheduler,
                        server));
    }

    /**
     * Stages the edited inventory (preserving the untouched Ender Chest) and commits it in one
     * server-main-thread turn.
     */
    static void commitOfflineInventory(
            Player viewer,
            UUID targetId,
            String displayName,
            VirtualInventory contents,
            OfflinePlayerEditSession session,
            MessageService messages,
            Scheduler scheduler,
            Server server) {
        OfflineCommitCoordinator.commit(
                viewer,
                targetId,
                displayName,
                session,
                InvseeGui.toSlottedItems(contents),
                session.snapshot().enderItems(),
                messages,
                scheduler,
                server);
    }
}
