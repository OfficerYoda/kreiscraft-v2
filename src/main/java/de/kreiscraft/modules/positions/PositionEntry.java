package de.kreiscraft.modules.positions;

import java.time.Instant;
import java.util.UUID;
import org.bukkit.Location;

/**
 * A single named waypoint belonging to one player. Stored in
 * {@code data/positions.yml} with optional provenance for backward compatibility.
 */
public record PositionEntry(Location location, UUID savedBy, String savedByName, Instant savedAt) {

    /** Creates a legacy entry without provenance. */
    public PositionEntry(Location location) {
        this(location, null, null, null);
    }
}
