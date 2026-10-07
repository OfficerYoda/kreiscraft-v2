package de.kreiscraft.modules.invsee.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.invsee.InvseeGui;
import de.kreiscraft.platform.OfflinePlayerData;
import de.kreiscraft.platform.OfflinePlayerSnapshot;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * {@code /enderchest <player>} (aliases: {@code ec}, {@code echest}) — shows the target's ender
 * chest.
 *
 * <p>Viewing and editing share the single {@code kreiscraft.invsee.use} permission: a viewer who
 * passes the command's authorization may modify the target's live ender chest directly. Offline
 * targets are shown as a read-only snapshot of their {@code EnderItems} read through {@link
 * OfflinePlayerData}.
 *
 * <p>The target argument completes online players plus every known-offline profile through the
 * shared {@link SuggestionProviders#onlineAndKnownOfflineProfiles} provider.
 */
@SuppressWarnings("UnstableApiUsage")
public final class EnderChestCommand {

    private EnderChestCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            Gui gui, MessageService messages, OfflinePlayerData offlineData, Server server) {
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
                                                openEnderChest(viewer, target, gui, messages);
                                            } else {
                                                lookupAndOpenOfflineEnderChest(
                                                        viewer, name, gui, messages, offlineData);
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        }))
                .build();
    }

    /** Package-private so tests can assert the localized, styled title without Brigadier dispatch. */
    static void openEnderChest(
            Player viewer, Player target, Gui gui, MessageService messages) {
        xyz.xenondevs.invui.gui.Gui invuiGui =
                InvseeGui.buildEnderChestEditable(target.getEnderChest());
        Component title = messages.renderFor(viewer, "invsee.gui-title-ec", "player", target.getName());
        gui.open(viewer, invuiGui, title);
    }

    /** Resolves the offline player and delegates to {@link #openOfflineEnderChest}. */
    private static void lookupAndOpenOfflineEnderChest(
            Player viewer,
            String name,
            Gui gui,
            MessageService messages,
            OfflinePlayerData offlineData) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        openOfflineEnderChest(viewer, offline, name, gui, messages, offlineData);
    }

    /**
     * Opens the read-only offline Ender Chest snapshot.
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
            OfflinePlayerData offlineData) {
        if (!offline.hasPlayedBefore()) {
            messages.send(viewer, "invsee.target-not-found", "player", displayName);
            return;
        }
        OfflinePlayerSnapshot snapshot;
        try {
            snapshot = offlineData.read(offline.getUniqueId());
        } catch (IOException e) {
            messages.send(viewer, "invsee.read-error");
            return;
        }
        if (snapshot.enderItems().isEmpty()) {
            messages.send(viewer, "invsee.offline-empty-ec", "player", displayName);
            return;
        }
        xyz.xenondevs.invui.gui.Gui invuiGui = InvseeGui.buildOfflineEnderChest(snapshot);
        Component title = messages.renderFor(viewer, "invsee.gui-title-ec", "player", displayName);
        gui.open(viewer, invuiGui, title);
    }
}
