package de.kreiscraft.modules.admin.commands;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;

/**
 * The temporary Variant A comparison board from the command-color prototype.
 *
 * <p>All four cards are visible in the same three-row chest. Cards are deliberately simple InvUI
 * items: they have no click handler, selection state, or persistence path.
 */
public final class CommandStylePreviewGui {

    private static final int[] CARD_SLOTS = {0, 2, 4, 6};
    private static final List<Palette> PALETTES =
            List.of(
                    new Palette(
                            "Classic",
                            Material.GOLD_BLOCK,
                            0xAAAAAA,
                            0xFFAA00,
                            0xFF55FF,
                            0x55FFFF,
                            0x55FF55,
                            0xFFFF55,
                            0xFF5555),
                    new Palette(
                            "Signal",
                            Material.LIGHT_BLUE_CONCRETE,
                            0xD7DCE2,
                            0x55FFFF,
                            0xAAAAFF,
                            0x5599FF,
                            0x55FF99,
                            0xFFCC55,
                            0xFF6677),
                    new Palette(
                            "Ember",
                            Material.ORANGE_CONCRETE,
                            0xC7C7C7,
                            0xFFFF55,
                            0xFFAA55,
                            0x55FFFF,
                            0x55FF55,
                            0xFFAA00,
                            0xFF5555),
                    new Palette(
                            "Requested Hybrid",
                            Material.PURPLE_CONCRETE,
                            0xD7DCE2,
                            0x55FFFF,
                            0xFFAA55,
                            0x5599FF,
                            0x55FF99,
                            0xFFCC55,
                            0xFF5555));

    private CommandStylePreviewGui() {}

    /** Builds four simultaneously visible, non-interactive palette cards. */
    public static Gui build() {
        Gui gui = Gui.empty(9, 3);
        for (int i = 0; i < PALETTES.size(); i++) {
            gui.setItem(CARD_SLOTS[i], Item.simple(PALETTES.get(i).card()));
        }
        return gui;
    }

    /** A real window title that previews all candidates together. */
    public static Component title() {
        Component title = Component.empty();
        for (int i = 0; i < PALETTES.size(); i++) {
            if (i > 0) {
                title = title.append(Component.text(" | ", TextColor.color(0xAAAAAA)));
            }
            Palette palette = PALETTES.get(i);
            title = title.append(Component.text(palette.name(), TextColor.color(palette.player())));
        }
        return title;
    }

    static int[] cardSlots() {
        return CARD_SLOTS.clone();
    }

    private record Palette(
            String name,
            Material material,
            int ordinary,
            int player,
            int reason,
            int value,
            int success,
            int warning,
            int error) {

        private ItemStack card() {
            ItemStack item = new ItemStack(material);
            item.editMeta(meta -> {
                meta.displayName(line(name, player));
                meta.lore(
                        List.of(
                                line("Opening inventory for Steve", ordinary, player),
                                line("Reason: Repeated griefing", ordinary, reason),
                                line("TPS: 19.98", ordinary, value),
                                line("Position Market was saved.", success),
                                line("Steve is already jailed.", warning),
                                line("Player Alex was not found.", error),
                                line("Inventory of Steve", ordinary, player)));
            });
            return item;
        }

        private static Component line(String text, int color) {
            return Component.text(text, TextColor.color(color));
        }

        private static Component line(String ordinary, int ordinaryColor, int roleColor) {
            int split = ordinary.lastIndexOf(' ');
            return Component.text(ordinary.substring(0, split + 1), TextColor.color(ordinaryColor))
                    .append(Component.text(ordinary.substring(split + 1), TextColor.color(roleColor)));
        }
    }
}
