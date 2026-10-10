package de.kreiscraft.modules.startsequence;

import org.bukkit.World;

public interface StartSequenceService {

    boolean hasStarted();

    /** Renders the countdown, then delegates one continuous world-border expansion on the given
     * world.
     *
     * @throws IllegalStateException if the season has already started (call {@link #reset()} first)
     */
    void trigger(World world);

    void reset();
}
