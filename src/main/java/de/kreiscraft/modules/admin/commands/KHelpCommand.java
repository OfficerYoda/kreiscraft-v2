package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.admin.AdminConfig;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;

public final class KHelpCommand {

    private KHelpCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Supplier<AdminConfig> configSupplier) {
        return Commands.literal("kreiscrafthelp")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.help"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    AdminConfig cfg = configSupplier.get();
                    messages.send(
                            sender,
                            "admin.help.text",
                            "website",
                            cfg.links().website().toString(),
                            "discord",
                            cfg.links().discord().toString(),
                            "youtube",
                            cfg.links().youtube().toString());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
