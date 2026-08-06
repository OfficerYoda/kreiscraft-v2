package de.kreiscraft.modules.pluginhider.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.pluginhider.PluginHiderService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public final class PluginHiderCommand {

    private PluginHiderCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            PluginHiderService service, MessageService messages) {
        return Commands.literal("pluginhider")
                .requires(src -> src.getSender().hasPermission("kreiscraft.pluginhider.admin"))
                .then(Commands.literal("add")
                        .then(Commands.argument("command", StringArgumentType.word())
                                .executes(ctx -> {
                                    String cmd = StringArgumentType.getString(ctx, "command");
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (service.add(cmd)) {
                                        messages.send(sender, "plugin-hider.added", "command", cmd);
                                    } else {
                                        messages.send(
                                                sender,
                                                "plugin-hider.already-present",
                                                "command",
                                                cmd);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("command", StringArgumentType.word())
                                .executes(ctx -> {
                                    String cmd = StringArgumentType.getString(ctx, "command");
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (service.remove(cmd)) {
                                        messages.send(sender, "plugin-hider.removed", "command", cmd);
                                    } else {
                                        messages.send(
                                                sender,
                                                "plugin-hider.not-present",
                                                "command",
                                                cmd);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            messages.send(sender, "plugin-hider.list-header");
                            Collection<String> allowed = service.allowedCommands();
                            String joined = allowed.isEmpty() ? "(none)" : String.join(", ", allowed);
                            sender.sendMessage(
                                    Component.text(joined, NamedTextColor.GRAY));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
