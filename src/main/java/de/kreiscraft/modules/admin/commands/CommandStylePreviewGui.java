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
    private static final List<Palette> PALETTES = List.of(Palette.values());

    private CommandStylePreviewGui() {}

    /** Builds four simultaneously visible, non-interactive palette cards. */
    public static Gui build() {
        Gui gui = Gui.empty(9, 3);
        for (int i = 0; i < PALETTES.size(); i++) {
            gui.setItem(CARD_SLOTS[i], Item.simple(PALETTES.get(i).card()));
        }
        return gui;
    }

    /** Builds a non-interactive page whose real window title previews one candidate. */
    public static Gui buildTitlePreview(Palette palette) {
        Gui gui = Gui.empty(9, 1);
        gui.setItem(4, Item.simple(palette.card()));
        return gui;
    }

    /** Neutral title for the simultaneous Variant A board. */
    public static Component boardTitle() {
        return Component.text("Command Message Style Comparison", TextColor.color(0xAAAAAA));
    }

    /** Genuine inventory title preview, with ordinary and player roles kept distinct. */
    public static Component title(Palette palette) {
        return palette.roleLine("Inventory of ", "Steve", palette.player);
    }

    static int[] cardSlots() {
        return CARD_SLOTS.clone();
    }

    public enum Palette {
        CLASSIC(
                "Classic", Material.GOLD_BLOCK, 0xAAAAAA, 0xFFAA00, 0xFF55FF, 0x55FFFF, 0x55FF55,
                0xFFFF55, 0xFF5555), SIGNAL(
                        "Signal", Material.LIGHT_BLUE_CONCRETE, 0xD7DCE2, 0x55FFFF, 0xAAAAFF, 0x5599FF,
                        0x55FF99, 0xFFCC55, 0xFF6677), EMBER(
                                "Ember", Material.ORANGE_CONCRETE, 0xC7C7C7, 0xFFFF55, 0xFFAA55, 0x55FFFF,
                                0x55FF55, 0xFFAA00, 0xFF5555), REQUESTED_HYBRID(
                                        "Requested Hybrid", Material.PURPLE_CONCRETE, 0xD7DCE2, 0x55FFFF, 0xFFAA55,
                                        0x5599FF, 0x55FF99, 0xFFCC55, 0xFF5555);

        private final String displayName;
        private final Material material;
        private final int ordinary;
        private final int player;
        private final int reason;
        private final int value;
        private final int success;
        private final int warning;
        private final int error;

        Palette(
                String displayName,
                Material material,
                int ordinary,
                int player,
                int reason,
                int value,
                int success,
                int warning,
                int error) {
            this.displayName = displayName;
            this.material = material;
            this.ordinary = ordinary;
            this.player = player;
            this.reason = reason;
            this.value = value;
            this.success = success;
            this.warning = warning;
            this.error = error;
        }

        private ItemStack card() {
            ItemStack item = new ItemStack(material);
            item.editMeta(meta -> {
                meta.displayName(line(displayName, player));
                meta.lore(
                        List.of(
                                roleLine("Opening inventory for ", "Steve", player),
                                roleLine("Reason: ", "Repeated griefing", reason),
                                roleLine("TPS: ", "19.98", value),
                                roleLine("", "Position Market was saved.", success),
                                roleLine("", "Steve is already jailed.", warning),
                                roleLine("", "Player Alex was not found.", error),
                                roleLine("Inventory of ", "Steve", player)));
            });
            return item;
        }

        private static Component line(String text, int color) {
            return Component.text(text, TextColor.color(color));
        }

        private Component roleLine(String ordinaryLabel, String roleValue, int roleColor) {
            return Component.text(ordinaryLabel, TextColor.color(ordinary))
                    .append(Component.text(roleValue, TextColor.color(roleColor)));
        }
    }
}
