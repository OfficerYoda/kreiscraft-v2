package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class HeadCommand {

    private HeadCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(MessageService messages) {
        return Commands.literal("head")
                .requires(
                        src -> src.getSender() instanceof Player
                                && src.getSender()
                                        .hasPermission("kreiscraft.admin.head"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    ItemStack held = player.getInventory().getItemInMainHand();
                    ItemStack helmet = player.getInventory().getHelmet();
                    int slot = player.getInventory().getHeldItemSlot();
                    player.getInventory().setHelmet(held.getType() == Material.AIR ? null : held);
                    player.getInventory()
                            .setItem(slot, helmet == null ? new ItemStack(Material.AIR) : helmet);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
