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
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.inventory.VirtualInventory;

/**
 * {@code /enderchest <player>} (aliases: {@code ec}, {@code echest}) — shows the target's ender
 * chest.
 *
 * <p>Viewing and editing share the single {@code kreiscraft.invsee.use} permission: a viewer who
 * passes the command's authorization may modify the target's live ender chest directly. Offline
 * targets are opened as an editable snapshot of their {@code EnderItems} through {@link
 * OfflinePlayerDataEditor} and committed through the two-phase offline-write boundary.
 *
 * <p>The target argument completes online players plus every known-offline profile through the
 * shared {@link SuggestionProviders#onlineAndKnownOfflineProfiles} provider.
 */
@SuppressWarnings("UnstableApiUsage")
public final class EnderChestCommand {

    private EnderChestCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            Gui gui,
            MessageService messages,
            OfflinePlayerDataEditor offlineEditor,
            Scheduler scheduler,
            Server server) {
        return build(gui, messages, offlineEditor, scheduler, server, () -> false);
    }

    public static LiteralCommandNode<CommandSourceStack> build(Gui gui, MessageService messages,
            OfflinePlayerDataEditor offlineEditor, Scheduler scheduler, Server server,
            BooleanSupplier eggModuleEnabled) {
        return Commands.literal("enderchest")
                .requires(src -> src.getSender().hasPermission("kreiscraft.invsee.use"))
                .then(
                        Commands.argument("target", StringArgumentType.word())
                                .suggests(
                                        SuggestionProviders.onlineAndKnownOfflineProfiles(
                                                server, "kreiscraft.invsee.use"))
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
                                                openEnderChest(viewer, target, gui, messages, eggModuleEnabled);
                                            } else {
                                                lookupAndOpenOfflineEnderChest(
                                                        viewer,
                                                        name,
                                                        gui,
                                                        messages,
                                                        offlineEditor,
                                                        scheduler,
                                                        server, eggModuleEnabled);
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        }))
                .build();
    }

    /** Package-private so tests can assert the localized, styled title without Brigadier dispatch. */
    static void openEnderChest(
            Player viewer, Player target, Gui gui, MessageService messages) {
        openEnderChest(viewer, target, gui, messages, () -> false);
    }

    static void openEnderChest(Player viewer, Player target, Gui gui, MessageService messages,
            BooleanSupplier eggModuleEnabled) {
        xyz.xenondevs.invui.gui.Gui invuiGui =
                InvseeGui.buildEnderChestEditable(target.getEnderChest(),
                        actor -> messages.send(actor, "dragon-egg.ender-chest-blocked"), eggModuleEnabled);
        Component title = messages.renderFor(viewer, "invsee.gui-title-ec", "player", target.getName());
        gui.open(viewer, invuiGui, title);
    }

    /** Resolves the offline player and delegates to {@link #openOfflineEnderChest}. */
    private static void lookupAndOpenOfflineEnderChest(
            Player viewer,
            String name,
            Gui gui,
            MessageService messages,
            OfflinePlayerDataEditor offlineEditor,
            Scheduler scheduler,
            Server server, BooleanSupplier eggModuleEnabled) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        openOfflineEnderChest(
                viewer, offline, name, gui, messages, offlineEditor, scheduler, server, eggModuleEnabled);
    }

    /**
     * Opens the editable offline Ender Chest snapshot.
     *
     * <p>Package-private so tests can inject a pre-built {@link OfflinePlayer} mock directly
     * without triggering {@code Bukkit.getOfflinePlayer} (which hits NMS state in tests).
     */
    static void openOfflineEnderChest(
            Player viewer,
            OfflinePlayer offline,
            String displayName,
            Gui gui,
            MessageService messages,
            OfflinePlayerDataEditor offlineEditor,
            Scheduler scheduler,
            Server server) {
        openOfflineEnderChest(viewer, offline, displayName, gui, messages, offlineEditor, scheduler, server,
                () -> false);
    }

    static void openOfflineEnderChest(Player viewer, OfflinePlayer offline, String displayName, Gui gui,
            MessageService messages, OfflinePlayerDataEditor offlineEditor, Scheduler scheduler, Server server,
            BooleanSupplier eggModuleEnabled) {
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
            messages.send(viewer, "invsee.offline-empty-ec", "player", displayName);
            return;
        }
        VirtualInventory contents = InvseeGui.offlineEnderChestContents(session.snapshot());
        xyz.xenondevs.invui.gui.Gui invuiGui = InvseeGui.buildOfflineEnderChest(contents,
                actor -> messages.send(actor, "dragon-egg.ender-chest-blocked"), eggModuleEnabled);
        Component title = messages.renderFor(viewer, "invsee.gui-title-ec", "player", displayName);
        gui.open(
                viewer,
                invuiGui,
                title,
                reason -> commitOfflineEnderChest(
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
     * Stages the edited Ender Chest (preserving the untouched inventory) and commits it in one
     * server-main-thread turn.
     */
    static void commitOfflineEnderChest(
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
                session.snapshot().inventory(),
                InvseeGui.toSlottedItems(contents),
                messages,
                scheduler,
                server);
    }
}
