package de.kreiscraft.modules.spawn;

import org.bukkit.util.Vector;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Serializable representation of a 3-D velocity vector.
 * Used in {@link SpawnLaunchConfig} because {@code org.bukkit.util.Vector}
 * has no registered Configurate TypeSerializer in the core serializer set.
 * The YAML format is {@code { x: 0.0, y: 5.0, z: 0.0 }}.
 */
@ConfigSerializable public record VelocityRecord(double x,double y,double z){

/** Converts to a Bukkit {@link Vector}. */
public Vector toVector(){return new Vector(x,y,z);}}
