package de.kreiscraft.modules.settingsgui.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.modules.settingsgui.SettingsGui.Page;
import de.kreiscraft.modules.settingsgui.SettingsNavigator;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * Builds {@code /settings} and its {@code admin} subcommand.
 *
 * <p>{@code /settings} opens the Player Settings root for any player with {@code
 * kreiscraft.settingsgui.use}. {@code /settings admin} opens the authorized Server Configuration
 * editor and additionally requires {@code kreiscraft.settingsgui.admin}; a player without it cannot
 * reach the subcommand.
 */
public final class SettingsCommand {

    /** Permission required to open the Player Settings GUI. */
    public static final String USE_PERMISSION = "kreiscraft.settingsgui.use";

    /** Permission required to open the Server Configuration editor. */
    public static final String ADMIN_PERMISSION = "kreiscraft.settingsgui.admin";

    private SettingsCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(SettingsNavigator navigator) {
        return Commands.literal("settings")
                .requires(src -> src.getSender().hasPermission(USE_PERMISSION))
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player player) {
                        navigator.open(player, Page.ROOT);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("admin")
                        .requires(src -> src.getSender().hasPermission(ADMIN_PERMISSION))
                        .executes(ctx -> {
                            if (ctx.getSource().getSender() instanceof Player player) {
                                navigator.openAdmin(player);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
