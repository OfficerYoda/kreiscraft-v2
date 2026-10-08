package de.kreiscraft.modules.positions;

import de.kreiscraft.core.message.MessageService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.ItemWrapper;

/**
 * Builds the InvUI {@link Gui} for the positions menu.
 *
 * <p>Layout: a 6-row window with a decorative border (blue glass pane corners,
 * gray glass pane edges) and up to 28 inner slots showing the player's saved
 * positions. Clicking a position item teleports the player when
 * {@code teleportOnClick} is {@code true}.
 */
public final class PositionsGui {

    private PositionsGui() {}

    /** Width of the GUI (fixed at the standard chest width). */
    private static final int WIDTH = 9;

    /**
     * Builds an InvUI Gui for the given positions.
     *
     * @param positions    the player's current positions (snapshot)
     * @param config       module config (for {@code teleportOnClick})
     * @param onTeleport   called when the player clicks a position item; only
     *                     invoked when {@code config.teleportOnClick()} is
     *                     {@code true}
     * @return a fully assembled {@link Gui} ready to be opened via
     *         {@link de.kreiscraft.shared.gui.Gui#open}
     */
    public static Gui build(
            Map<String, PositionEntry> positions,
            PositionsConfig config,
            BiConsumer<Player, PositionEntry> onTeleport) {
        return build(positions, config, null, onTeleport);
    }

    /** Builds a localized GUI whose item lore includes position provenance. */
    public static Gui build(
            Map<String, PositionEntry> positions,
            PositionsConfig config,
            MessageService messages,
            BiConsumer<Player, PositionEntry> onTeleport) {

        int rows = config.guiRows();
        if (rows < 2) {
            rows = 2; // need at least top and bottom border rows

            // Build the row structure strings dynamically for the given row count.
        }
        String[] structure = buildStructure(rows);

        // Border items
        Item blueCorner = Item.simple(createItemStack(
                Material.BLUE_STAINED_GLASS_PANE, Component.text(" "), List.of()));
        Item grayEdge = Item.simple(createItemStack(
                Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of()));

        Gui gui =
                Gui.builder()
                        .setStructure(structure)
                        .addIngredient('B', blueCorner)
                        .addIngredient('E', grayEdge)
                        .addIngredient('G', grayEdge)
                        .build();

        // Inner slots: rows 1..(rows-2), columns 1..7
        List<Map.Entry<String, PositionEntry>> entries =
                new ArrayList<>(positions.entrySet());
        int innerRows = rows - 2;
        for (int r = 0; r < innerRows; r++) {
            for (int c = 0; c < 7; c++) {
                int slot = (r + 1) * WIDTH + (c + 1);
                int idx = r * 7 + c;
                if (idx < entries.size()) {
                    Map.Entry<String, PositionEntry> e = entries.get(idx);
                    gui.setItem(slot, createPositionItem(e.getKey(), e.getValue(),
                            config.teleportOnClick(), messages, onTeleport));
                }
            }
        }

        return gui;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String[] buildStructure(int rows) {
        String[] lines = new String[rows];
        lines[0] = "B E E E E E E E B";
        for (int i = 1; i < rows - 1; i++) {
            lines[i] = "G . . . . . . . G";
        }
        lines[rows - 1] = "B E E E E E E E B";
        return lines;
    }

    private static Item createPositionItem(
            String name,
            PositionEntry entry,
            boolean teleportOnClick,
            MessageService messages,
            BiConsumer<Player, PositionEntry> onTeleport) {

        Material material = materialFor(entry.location());

        String coordLine = entry.location().getWorld() != null
                ? entry.location().getWorld().getName()
                        + " "
                        + (int) entry.location().getX()
                        + " "
                        + (int) entry.location().getY()
                        + " "
                        + (int) entry.location().getZ()
                : "?";

        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                if (messages == null) {
                    return new ItemWrapper(createItemStack(
                            material, Component.text(name), List.of(Component.text(coordLine))));
                }
                String saver = entry.savedByName() == null
                        ? plain(messages.renderFor(viewer, "positions.gui-legacy"))
                        : entry.savedByName();
                String savedAt = entry.savedAt() == null
                        ? plain(messages.renderFor(viewer, "positions.gui-legacy"))
                        : DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                .withLocale(messages.localeFor(viewer))
                                .withZone(ZoneId.of("Europe/Berlin"))
                                .format(entry.savedAt());
                List<Component> lore = new ArrayList<>();
                lore.add(messages.renderFor(viewer, "positions.gui-location", "location", coordLine));
                lore.add(messages.renderFor(viewer, "positions.gui-saver", "player", saver));
                lore.add(messages.renderFor(viewer, "positions.gui-saved-at", "time", savedAt));
                if (teleportOnClick) {
                    lore.add(messages.renderFor(viewer, "positions.gui-teleport-hint"));
                }
                return new ItemWrapper(createItemStack(
                        material,
                        messages.renderFor(viewer, "positions.gui-item-title", "name", name),
                        lore));
            }

            @Override
            public void handleClick(
                    ClickType clickType, Player player, Click click) {
                if (teleportOnClick
                        && player.hasPermission("kreiscraft.positions.teleport")) {
                    onTeleport.accept(player, entry);
                }
            }
        };
    }

    private static ItemStack createItemStack(
            Material material, Component displayName, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(displayName.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
        });
        return stack;
    }

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }

    /**
     * Selects the representative block material for a world environment.
     *
     * <ul>
     *   <li>Nether → {@link Material#NETHERRACK}
     *   <li>The End → {@link Material#END_STONE}
     *   <li>Overworld / null → {@link Material#GRASS_BLOCK}
     * </ul>
     */
    static Material materialFor(org.bukkit.Location location) {
        if (location.getWorld() == null) {
            return Material.GRASS_BLOCK;
        }
        return switch (location.getWorld().getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }
}
