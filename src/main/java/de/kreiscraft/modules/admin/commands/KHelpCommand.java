package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.admin.AdminConfig;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

public final class KHelpCommand {

    private KHelpCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, AdminConfig config) {
        return Commands.literal("khelp")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.help"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    messages.send(
                            sender,
                            "admin.help.text",
                            "website",
                            config.links().website().toString(),
                            "discord",
                            config.links().discord().toString(),
                            "youtube",
                            config.links().youtube().toString());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
