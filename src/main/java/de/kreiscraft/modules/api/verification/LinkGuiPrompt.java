package de.kreiscraft.modules.api.verification;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.shared.gui.Gui;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;

/**
 * Opens a 3×9 chest GUI for the target player asking them to accept or decline a browser link
 * request.
 *
 * <p>Layout:
 *
 * <pre>
 * Row 0: filler (black glass pane), except slot 4 = paper item with browser description
 * Row 1: filler + slot 2 = green wool (Accept) + slot 6 = red wool (Decline) + filler
 * Row 2: filler
 * </pre>
 *
 * <p>Clicking "Accept" closes the window and runs {@code onAccept}. Clicking "Decline" closes the
 * window and runs {@code onDecline}. Clicking filler does nothing.
 */
public final class LinkGuiPrompt {

    private static final int WIDTH = 9;

    private final Gui gui;
    private final MessageService messages;

    public LinkGuiPrompt(Gui gui, MessageService messages) {
        this.gui = gui;
        this.messages = messages;
    }

    /**
     * Opens the link confirmation GUI for {@code target}.
     *
     * @param target             the Bukkit player who receives the prompt
     * @param browserDescription human-readable identifier for the browser session (e.g. "browser
     *                           #a1b2")
     * @param onAccept           runnable invoked on the main thread when the player clicks Accept
     * @param onDecline          runnable invoked on the main thread when the player clicks Decline
     */
    public void open(
            Player target,
            String browserDescription,
            Runnable onAccept,
            Runnable onDecline) {

        // Build the InvUI Gui
        xyz.xenondevs.invui.gui.Gui invui = buildInvUiGui(browserDescription, onAccept, onDecline);

        // Title from MessageService
        Component title =
                messages.render("api.link-prompt.title", "browser", browserDescription);

        gui.open(target, invui, title);
    }

    // -----------------------------------------------------------------------
    // InvUI Gui builder
    // -----------------------------------------------------------------------

    /**
     * Builds the raw InvUI {@link xyz.xenondevs.invui.gui.Gui} for the 3×9 layout.
     *
     * <p>Structure:
     *
     * <pre>
     * F F F F I F F F F    (row 0: filler + info paper at slot 4)
     * F F A F F F D F F    (row 1: accept at col 2, decline at col 6)
     * F F F F F F F F F    (row 2: filler)
     * </pre>
     */
    xyz.xenondevs.invui.gui.Gui buildInvUiGui(
            String browserDescription, Runnable onAccept, Runnable onDecline) {

        // Filler item — black stained-glass pane, empty name
        SimpleItem filler =
                new SimpleItem(
                        new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                                .setDisplayName(
                                        new AdventureComponentWrapper(Component.text(" "))));

        // Info item — paper with browser description
        Component infoName =
                messages.render("api.link-prompt.info", "browser", browserDescription);
        SimpleItem infoItem =
                new SimpleItem(
                        new ItemBuilder(Material.PAPER)
                                .setDisplayName(new AdventureComponentWrapper(infoName)));

        // Accept item — green wool
        Component acceptName = messages.render("api.link-prompt.accept");
        AbstractItem acceptItem = createClickItem(Material.GREEN_WOOL, acceptName, onAccept);

        // Decline item — red wool
        Component declineName = messages.render("api.link-prompt.decline");
        AbstractItem declineItem = createClickItem(Material.RED_WOOL, declineName, onDecline);

        xyz.xenondevs.invui.gui.Gui invui =
                xyz.xenondevs.invui.gui.Gui.normal()
                        .setStructure(
                                "F F F F I F F F F",
                                "F F A F F F D F F",
                                "F F F F F F F F F")
                        .addIngredient('F', filler)
                        .addIngredient('I', infoItem)
                        .addIngredient('A', acceptItem)
                        .addIngredient('D', declineItem)
                        .build();

        return invui;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates an {@link AbstractItem} that, when clicked, closes the player's open inventory and
     * invokes the supplied callback.
     */
    private static AbstractItem createClickItem(
            Material material, Component displayName, Runnable onClick) {
        ItemProvider provider =
                new ItemBuilder(material)
                        .setDisplayName(new AdventureComponentWrapper(displayName));

        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return provider;
            }

            @Override
            public void handleClick(
                    ClickType clickType, Player player, InventoryClickEvent event) {
                player.closeInventory();
                onClick.run();
            }
        };
    }
}
