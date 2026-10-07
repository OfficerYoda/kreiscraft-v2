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
                .executes(context -> openBoard(context.getSource().getSender(), messages, previewWindow))
                .then(
                        Commands.literal("title")
                                .then(titleNode("classic", CommandStylePreviewGui.Palette.CLASSIC, messages,
                                        previewWindow))
                                .then(titleNode("signal", CommandStylePreviewGui.Palette.SIGNAL, messages,
                                        previewWindow))
                                .then(titleNode("ember", CommandStylePreviewGui.Palette.EMBER, messages, previewWindow))
                                .then(titleNode("hybrid", CommandStylePreviewGui.Palette.REQUESTED_HYBRID, messages,
                                        previewWindow)))
                .build();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> titleNode(
            String name, CommandStylePreviewGui.Palette palette, MessageService messages, PreviewWindow previewWindow) {
        return Commands.literal(name)
                .executes(context -> openTitle(context.getSource().getSender(), palette, messages, previewWindow));
    }

    private static int openBoard(
            org.bukkit.command.CommandSender sender, MessageService messages, PreviewWindow previewWindow) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "core.player-only");
            return Command.SINGLE_SUCCESS;
        }
        messages.send(player, "admin.style-preview.temporary");
        previewWindow.open(player, CommandStylePreviewGui.build(), CommandStylePreviewGui.boardTitle());
        messages.send(player, "admin.style-preview.handoff");
        return Command.SINGLE_SUCCESS;
    }

    private static int openTitle(
            org.bukkit.command.CommandSender sender,
            CommandStylePreviewGui.Palette palette,
            MessageService messages,
            PreviewWindow previewWindow) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "core.player-only");
            return Command.SINGLE_SUCCESS;
        }
        messages.send(player, "admin.style-preview.temporary");
        previewWindow.open(player, CommandStylePreviewGui.buildTitlePreview(palette),
                CommandStylePreviewGui.title(palette));
        messages.send(player, "admin.style-preview.handoff");
        return Command.SINGLE_SUCCESS;
    }

    /** Production adapter for the shared GUI facade, isolated for command-behavior tests. */
    @FunctionalInterface
    public interface PreviewWindow {
        void open(Player player, xyz.xenondevs.invui.gui.Gui board, net.kyori.adventure.text.Component title);
    }
}
