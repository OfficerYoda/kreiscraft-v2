package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ItemCommand {

    private ItemCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(MessageService messages) {
        return Commands.literal("item")
                .requires(
                        src -> src.getSender() instanceof Player
                                && src.getSender()
                                        .hasPermission("kreiscraft.admin.item"))
                .then(Commands.argument("material", StringArgumentType.word())
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            String matName = StringArgumentType.getString(ctx, "material");
                            Material mat = Material.matchMaterial(matName);
                            if (mat == null || !mat.isItem()) {
                                messages.send(
                                        player,
                                        "admin.usage",
                                        "usage",
                                        "/item <material> [count]");
                                return Command.SINGLE_SUCCESS;
                            }
                            player.getInventory().addItem(new ItemStack(mat, 1));
                            messages.send(
                                    player,
                                    "admin.item-given",
                                    "count",
                                    1,
                                    "item",
                                    mat.name().toLowerCase());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 1024))
                                .executes(ctx -> {
                                    Player player = (Player) ctx.getSource().getSender();
                                    String matName = StringArgumentType.getString(ctx, "material");
                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    Material mat = Material.matchMaterial(matName);
                                    if (mat == null || !mat.isItem()) {
                                        messages.send(
                                                player,
                                                "admin.usage",
                                                "usage",
                                                "/item <material> [count]");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    // Give in stacks of maxStackSize to avoid overflow
                                    int remaining = count;
                                    while (remaining > 0) {
                                        int give = Math.min(remaining, mat.getMaxStackSize());
                                        player.getInventory().addItem(new ItemStack(mat, give));
                                        remaining -= give;
                                    }
                                    messages.send(
                                            player,
                                            "admin.item-given",
                                            "count",
                                            count,
                                            "item",
                                            mat.name().toLowerCase());
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
