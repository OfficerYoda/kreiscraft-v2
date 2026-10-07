package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

/**
 * Temporary, administrator-only comparison board for the Command Message Style approval checkpoint.
 *
 * <p>The command intentionally only displays candidates. It persists no choice; ticket #60 owns the
 * human decision and the subsequent production style rollout.
 */
public final class CommandStylePreviewCommand {

    private static final String PERMISSION = "kreiscraft.admin.style-preview";
    private static final List<Palette> PALETTES =
            List.of(
                    new Palette(
                            "Classic",
                            0xAAAAAA,
                            0xFFAA00,
                            0xFF55FF,
                            0x55FFFF,
                            0x55FF55,
                            0xFFFF55,
                            0xFF5555),
                    new Palette(
                            "Signal",
                            0xD7DCE2,
                            0x55FFFF,
                            0xAAAAFF,
                            0x5599FF,
                            0x55FF99,
                            0xFFCC55,
                            0xFF6677),
                    new Palette(
                            "Ember",
                            0xC7C7C7,
                            0xFFFF55,
                            0xFFAA55,
                            0x55FFFF,
                            0x55FF55,
                            0xFFAA00,
                            0xFF5555),
                    new Palette(
                            "Requested Hybrid",
                            0xD7DCE2,
                            0x55FFFF,
                            0xFFAA55,
                            0x5599FF,
                            0x55FF99,
                            0xFFCC55,
                            0xFF5555));

    private CommandStylePreviewCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(MessageService messages) {
        return Commands.literal("commandstylepreview")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(context -> {
                    show(context.getSource().getSender(), messages);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private static void show(CommandSender sender, MessageService messages) {
        messages.send(sender, "admin.style-preview.temporary");
        for (Palette palette : PALETTES) {
            messages.send(sender, "admin.style-preview.header", "palette", palette.text(palette.name()));
            messages.send(
                    sender,
                    "admin.style-preview.inventory-opening",
                    "line",
                    palette.inventoryOpening());
            messages.send(sender, "admin.style-preview.reason", "line", palette.reasonLine());
            messages.send(sender, "admin.style-preview.value", "line", palette.valueLine());
            messages.send(sender, "admin.style-preview.success", "line", palette.successLine());
            messages.send(sender, "admin.style-preview.warning", "line", palette.warningLine());
            messages.send(sender, "admin.style-preview.error", "line", palette.errorLine());
            messages.send(
                    sender,
                    "admin.style-preview.inventory-title",
                    "line",
                    palette.inventoryTitle());
        }
        messages.send(sender, "admin.style-preview.handoff");
    }

    private record Palette(
            String name,
            int ordinary,
            int player,
            int reason,
            int value,
            int success,
            int warning,
            int error) {

        private Component text(String value) {
            return Component.text(value, TextColor.color(ordinary));
        }

        private Component inventoryOpening() {
            return line("[Kreiscraft] ", ordinary)
                    .append(line("Opening inventory for ", ordinary))
                    .append(line("Steve", player))
                    .append(line(".", ordinary));
        }

        private Component reasonLine() {
            return line("[Kreiscraft] ", ordinary)
                    .append(line("Steve was jailed. Reason: ", ordinary))
                    .append(line("Repeated griefing", reason));
        }

        private Component valueLine() {
            return line("[Kreiscraft] TPS: ", ordinary).append(line("19.98", value));
        }

        private Component successLine() {
            return line("[Kreiscraft] Position Market was saved.", success);
        }

        private Component warningLine() {
            return line("[Kreiscraft] Steve is already jailed.", warning);
        }

        private Component errorLine() {
            return line("[Kreiscraft] Player Alex was not found.", error);
        }

        private Component inventoryTitle() {
            return line("Inventory title: ", ordinary)
                    .append(line("Inventory of Steve", player));
        }

        private static Component line(String value, int color) {
            return Component.text(value, TextColor.color(color));
        }
    }
}
