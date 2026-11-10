package de.kreiscraft.platform;

import org.bukkit.Location;

/** One owned native flight phase. Its callback runs inside the dragon's server tick. */
public interface DragonFlightControl extends AutoCloseable {
    boolean active();

    void move(Location destination);

    @Override
    void close();
}
