package de.kreiscraft.modules.positions;

import org.bukkit.Location;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * A single named waypoint belonging to one player. Stored in
 * {@code data/positions.yml} as a {@link ConfigSerializable} record so that
 * Configurate's ObjectMapper handles the round-trip via
 * {@link de.kreiscraft.core.config.serializers.LocationSerializer}.
 */
@ConfigSerializable public record PositionEntry(Location location){}
