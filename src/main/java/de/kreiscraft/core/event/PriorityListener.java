package de.kreiscraft.core.event;

import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Marker for listeners that document a preferred event priority.
 * Bukkit reads the priority from the @EventHandler annotation, so this
 * interface is purely for readability/documentation in Plan 1.
 * Future refactors may wire it up for actual dispatch.
 */
public interface PriorityListener extends Listener {
    default EventPriority priority() {
        return EventPriority.NORMAL;
    }
}
