package de.kreiscraft.modules.settingsgui.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleEnablement;
import de.kreiscraft.modules.settingsgui.SettingsGui;
import de.kreiscraft.modules.settingsgui.SettingsGui.Page;
import de.kreiscraft.modules.settingsgui.SettingsService;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class SettingsCommand {

    private SettingsCommand() {}

    /**
     * Builds the {@code /settings} command node. The command opens the module-aware Player Settings
     * GUI for the invoking player and wires page navigation back through itself.
     */
    public static LiteralCommandNode<CommandSourceStack> build(
            SettingsService service,
            ModuleEnablement modules,
            MessageService messages,
            Gui gui) {
        return Commands.literal("settings")
                .requires(src -> src.getSender().hasPermission("kreiscraft.settingsgui.use"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    open(player, service, modules, messages, gui, Page.ROOT);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private static void open(
            Player player,
            SettingsService service,
            ModuleEnablement modules,
            MessageService messages,
            Gui gui,
            Page page) {
        SettingsGui.Navigator navigator =
                (viewer, target) -> open(viewer, service, modules, messages, gui, target);
        xyz.xenondevs.invui.gui.Gui invui =
                switch (page) {
                    case ROOT -> SettingsGui.buildRoot(service, modules, messages, player, navigator);
                    case EFFECTS -> SettingsGui.buildEffects(service, messages, navigator);
                };
        Component title = messages.renderFor(player, page.titleKey());
        gui.open(player, invui, title);
    }
}
