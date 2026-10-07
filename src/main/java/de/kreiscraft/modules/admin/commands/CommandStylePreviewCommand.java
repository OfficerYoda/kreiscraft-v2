package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/** Temporary, administrator-only Command Message Style approval checkpoint. */
public final class CommandStylePreviewCommand {

    private static final String PERMISSION = "kreiscraft.admin.style-preview";

    private CommandStylePreviewCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, PreviewWindow previewWindow) {
        return Commands.literal("commandstylepreview")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(context -> {
                    if (!(context.getSource().getSender() instanceof Player player)) {
                        messages.send(context.getSource().getSender(), "core.player-only");
                        return Command.SINGLE_SUCCESS;
                    }
                    messages.send(player, "admin.style-preview.temporary");
                    previewWindow.open(player, CommandStylePreviewGui.build(), CommandStylePreviewGui.title());
                    messages.send(player, "admin.style-preview.handoff");
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    /** Production adapter for the shared GUI facade, isolated for command-behavior tests. */
    @FunctionalInterface
    public interface PreviewWindow {
        void open(Player player, xyz.xenondevs.invui.gui.Gui board, net.kyori.adventure.text.Component title);
    }
}
