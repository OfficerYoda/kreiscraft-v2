package de.kreiscraft.modules.status.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.status.StatusService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

public final class StatusCommand {

    private static final int MAX_VISIBLE_LENGTH = 32;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private StatusCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            StatusService service, MessageService messages) {
        return Commands.literal("status")
                .then(Commands.literal("prefix")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .requires(src -> src.getSender()
                                        .hasPermission("kreiscraft.status.set.self"))
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String text = StringArgumentType.getString(ctx, "text");
                                    if (!validateLength(text)) {
                                        messages.send(player, "status.too-long");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    service.setPrefix(player.getUniqueId(), text);
                                    messages.send(player, "status.prefix-set");
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("suffix")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .requires(src -> src.getSender()
                                        .hasPermission("kreiscraft.status.set.self"))
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String text = StringArgumentType.getString(ctx, "text");
                                    if (!validateLength(text)) {
                                        messages.send(player, "status.too-long");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    service.setSuffix(player.getUniqueId(), text);
                                    messages.send(player, "status.suffix-set");
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private static boolean validateLength(String miniMessageText) {
        return MINI_MESSAGE.stripTags(miniMessageText).length() <= MAX_VISIBLE_LENGTH;
    }
}
