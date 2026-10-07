package de.kreiscraft.modules.settingsgui.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.settingsgui.CombatDisplay;
import de.kreiscraft.modules.settingsgui.DefaultSettingsService;
import de.kreiscraft.modules.settingsgui.SettingsService;
import de.kreiscraft.shared.gui.Gui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.ItemWrapper;

public final class SettingsCommand {

    private SettingsCommand() {}

    /**
     * Builds the {@code /settings} command node. {@code service} is the concrete implementation
     * so that {@link ClickThroughItem} can call the debounce-aware {@link
     * DefaultSettingsService#toggleClickThrough(UUID)} without exposing it on the public
     * {@link SettingsService} interface.
     */
    public static LiteralCommandNode<CommandSourceStack> build(
            DefaultSettingsService service, MessageService messages, Gui gui) {
        return Commands.literal("settings")
                .requires(src -> src.getSender().hasPermission("kreiscraft.settingsgui.use"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    openGui(player, service, messages, gui);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private static void openGui(
            Player player, DefaultSettingsService service, MessageService messages, Gui gui) {
        UUID playerId = player.getUniqueId();

        xyz.xenondevs.invui.gui.Gui invui =
                xyz.xenondevs.invui.gui.Gui.builder()
                        .setStructure(
                                ". . . . . . . . .",
                                ". T . C . W . W .",
                                ". . . . . . . . .")
                        .addIngredient('T', new ClickThroughItem(playerId, service, messages))
                        .addIngredient('C', new CombatDisplayItem(playerId, service, messages))
                        .addIngredient('W', new WipItem(messages))
                        .build();

        Component title = messages.render("settings-gui.gui-title");
        gui.open(player, invui, title);
    }

    // --- GUI item implementations ---

    private static final class ClickThroughItem extends AbstractItem {

        private final UUID playerId;
        private final DefaultSettingsService service;
        private final MessageService messages;

        ClickThroughItem(UUID playerId, DefaultSettingsService service, MessageService messages) {
            this.playerId = playerId;
            this.service = service;
            this.messages = messages;
        }

        @Override
        public ItemProvider getItemProvider(Player viewer) {
            boolean on = service.isClickThroughEnabled(playerId);
            Material mat = on ? Material.LIME_DYE : Material.RED_DYE;
            String key = on ? "settings-gui.click-through-on" : "settings-gui.click-through-off";
            Component name = messages.render(key);
            ItemStack stack = new ItemStack(mat);
            stack.editMeta(m -> m.displayName(name));
            return new ItemWrapper(stack);
        }

        @Override
        public void handleClick(ClickType clickType, Player player, Click click) {
            if (service.toggleClickThrough(player.getUniqueId())) {
                notifyWindows();
            }
        }
    }

    private static final class CombatDisplayItem extends AbstractItem {

        private final UUID playerId;
        private final SettingsService service;
        private final MessageService messages;

        CombatDisplayItem(UUID playerId, SettingsService service, MessageService messages) {
            this.playerId = playerId;
            this.service = service;
            this.messages = messages;
        }

        @Override
        public ItemProvider getItemProvider(Player viewer) {
            CombatDisplay display = service.getCombatDisplay(playerId);
            Material mat = materialFor(display);
            String key = langKeyFor(display);
            Component name = messages.render(key);
            ItemStack stack = new ItemStack(mat);
            stack.editMeta(m -> m.displayName(name));
            return new ItemWrapper(stack);
        }

        @Override
        public void handleClick(ClickType clickType, Player player, Click click) {
            CombatDisplay current = service.getCombatDisplay(player.getUniqueId());
            CombatDisplay next = nextDisplay(current);
            service.setCombatDisplay(player.getUniqueId(), next);
            notifyWindows();
        }

        private static Material materialFor(CombatDisplay display) {
            return switch (display) {
                case CHAT -> Material.IRON_SWORD;
                case ACTIONBAR -> Material.DIAMOND_SWORD;
                case TITLE -> Material.NETHERITE_SWORD;
            };
        }

        private static String langKeyFor(CombatDisplay display) {
            return switch (display) {
                case CHAT -> "settings-gui.combat-display-chat";
                case ACTIONBAR -> "settings-gui.combat-display-actionbar";
                case TITLE -> "settings-gui.combat-display-title";
            };
        }

        private static CombatDisplay nextDisplay(CombatDisplay current) {
            CombatDisplay[] values = CombatDisplay.values();
            return values[(current.ordinal() + 1) % values.length];
        }
    }

    private static final class WipItem extends AbstractItem {

        private final MessageService messages;

        WipItem(MessageService messages) {
            this.messages = messages;
        }

        @Override
        public ItemProvider getItemProvider(Player viewer) {
            Component name = messages.render("settings-gui.wip");
            ItemStack stack = new ItemStack(Material.BARRIER);
            stack.editMeta(m -> m.displayName(name));
            return new ItemWrapper(stack);
        }

        @Override
        public void handleClick(ClickType clickType, Player player, Click click) {
            // WIP: no action
        }
    }
}
