package de.kreiscraft.shared.gui;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks open GUI windows so the plugin can close them cleanly on {@code onDisable()}. Independent
 * of any particular GUI library — the {@link Handle} abstraction lets the Gui facade register
 * InvUI-backed handles without pulling library types into non-GUI tests.
 */
public final class GuiRegistry {

    private static final Logger LOGGER = Logger.getLogger(GuiRegistry.class.getName());

    private final Set<Handle> open = ConcurrentHashMap.newKeySet();

    /** Abstraction over an open GUI window; closing it disposes the underlying inventory view. */
    public interface Handle {
        void close();
    }

    public void register(Handle handle) {
        open.add(handle);
    }

    public void unregister(Handle handle) {
        open.remove(handle);
    }

    /** Closes every open handle and clears the registry. Exceptions are caught and logged. */
    public void closeAll() {
        for (Handle h : open) {
            try {
                h.close();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "GUI handle failed to close", e);
            }
        }
        open.clear();
    }

    public int openCount() {
        return open.size();
    }
}
