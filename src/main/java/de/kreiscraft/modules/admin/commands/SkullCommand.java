package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class SkullCommand {

    private SkullCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Server server) {
        return Commands.literal("skull")
                .requires(
                        src -> src.getSender() instanceof Player
                                && src.getSender()
                                        .hasPermission("kreiscraft.admin.skull"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    giveSkull(player, player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            Player executor = (Player) ctx.getSource().getSender();
                            String targetName = StringArgumentType.getString(ctx, "player");
                            @SuppressWarnings("deprecation")
                            org.bukkit.OfflinePlayer offlineTarget =
                                    server.getOfflinePlayer(targetName);
                            giveSkullOffline(executor, offlineTarget, targetName);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static void giveSkull(Player recipient, Player owner) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(owner);
        skull.setItemMeta(meta);
        recipient.getInventory().addItem(skull);
    }

    @SuppressWarnings("deprecation")
    private static void giveSkullOffline(
            Player recipient, org.bukkit.OfflinePlayer owner, String ownerName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(owner);
        skull.setItemMeta(meta);
        recipient.getInventory().addItem(skull);
    }
}
