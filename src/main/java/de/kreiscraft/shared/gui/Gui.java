package de.kreiscraft.shared.gui;

import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import xyz.xenondevs.invui.window.Window;
import xyz.xenondevs.invui.window.AnvilWindow;

/**
 * Thin facade over InvUI. The plugin's only sanctioned way to open an inventory GUI — every module
 * goes through this class so windows are tracked in {@link GuiRegistry} and closed cleanly on
 * plugin disable.
 *
 * <p>The facade is intentionally minimal. Modules compose their InvUI {@link
 * xyz.xenondevs.invui.gui.Gui} using InvUI's builders (Item, AbstractItem, layouts, etc.);
 * this class only handles the Window boilerplate and registry bookkeeping.
 */
public final class Gui {

    private final GuiRegistry registry;

    public Gui(GuiRegistry registry) {
        this.registry = registry;
    }

    /**
     * Opens a single-viewer window backed by {@code invui} for {@code viewer}.
     *
     * @return a handle whose {@link GuiRegistry.Handle#close()} closes the window
     *     programmatically. The handle is also registered with the {@link GuiRegistry} and
     *     auto-unregisters when the player closes the window normally.
     */
    public GuiRegistry.Handle open(
            Player viewer, xyz.xenondevs.invui.gui.Gui invui, Component title) {
        return open(viewer, invui, title, reason -> {
        });
    }

    /**
     * Opens a single-viewer window and reports its close reason to {@code onClose}.
     *
     * <p>The callback runs after the registry bookkeeping, so a caller that reopens a window from
     * the callback still sees a consistent registry. {@code onClose} is invoked for every close,
     * including the implicit close that happens when another window replaces this one; use the
     * reason to distinguish a player-requested close from navigation.
     *
     * @return a handle whose {@link GuiRegistry.Handle#close()} closes the window
     *     programmatically. The handle is also registered with the {@link GuiRegistry} and
     *     auto-unregisters when the player closes the window normally.
     */
    public GuiRegistry.Handle open(
            Player viewer,
            xyz.xenondevs.invui.gui.Gui invui,
            Component title,
            Consumer<InventoryCloseEvent.Reason> onClose) {
        Objects.requireNonNull(onClose, "onClose");
        Window window =
                Window.builder()
                        .setViewer(viewer)
                        .setUpperGui(invui)
                        .setTitle(title)
                        .build();

        GuiRegistry.Handle handle = window::close;
        registry.register(handle);
        window.addCloseHandler(reason -> {
            registry.unregister(handle);
            onClose.accept(reason);
        });
        window.open();
        return handle;
    }

    /** Opens a tracked anvil window with a custom lower inventory. */
    public GuiRegistry.Handle openAnvil(
            Player viewer,
            xyz.xenondevs.invui.gui.Gui upper,
            xyz.xenondevs.invui.gui.Gui lower,
            Component title,
            Consumer<String> onRename,
            Consumer<InventoryCloseEvent.Reason> onClose) {
        AnvilWindow window =
                AnvilWindow.builder()
                        .setViewer(viewer)
                        .setUpperGui(upper)
                        .setLowerGui(lower)
                        .setTitle(title)
                        .setTextFieldAlwaysEnabled(true)
                        .setResultAlwaysValid(true)
                        .addRenameHandler(onRename)
                        .build();
        GuiRegistry.Handle handle = window::close;
        registry.register(handle);
        window.addCloseHandler(reason -> {
            registry.unregister(handle);
            onClose.accept(reason);
        });
        window.open();
        return handle;
    }
}
