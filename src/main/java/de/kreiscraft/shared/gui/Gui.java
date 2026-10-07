package de.kreiscraft.shared.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.window.Window;

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
        Window window =
                Window.builder()
                        .setViewer(viewer)
                        .setUpperGui(invui)
                        .setTitle(title)
                        .build();

        GuiRegistry.Handle handle = window::close;
        registry.register(handle);
        window.addCloseHandler(reason -> registry.unregister(handle));
        window.open();
        return handle;
    }
}
