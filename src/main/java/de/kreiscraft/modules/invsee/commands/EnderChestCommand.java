package de.kreiscraft.modules.invsee.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.invsee.InvseeGui;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * {@code /enderchest <player>} (aliases: {@code ec}, {@code echest}) — shows the target's ender
 * chest.
 *
 * <p>Viewing and editing share the single {@code kreiscraft.invsee.use} permission: a viewer who
 * passes the command's authorization may modify the target's live ender chest directly.
 *
 * <p>The target argument completes online players plus every known-offline profile through the
 * shared {@link SuggestionProviders#onlineAndKnownOfflineProfiles} provider.
 *
 * <p>Offline targets are not supported yet (ender-chest data is not included in offline NBT for the
 * same player-data file as the main inventory).
 */
@SuppressWarnings("UnstableApiUsage")
public final class EnderChestCommand {

    private EnderChestCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            Gui gui, MessageService messages, Server server) {
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

    /** Package-private so tests can assert the localized, styled title without Brigadier dispatch. */
    static void openEnderChest(
            Player viewer, Player target, Gui gui, MessageService messages) {
        xyz.xenondevs.invui.gui.Gui invuiGui =
                InvseeGui.buildEnderChestEditable(target.getEnderChest());
        Component title = messages.renderFor(viewer, "invsee.gui-title-ec", "player", target.getName());
        gui.open(viewer, invuiGui, title);
    }
}
