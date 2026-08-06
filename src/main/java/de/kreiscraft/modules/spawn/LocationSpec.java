package de.kreiscraft.modules.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Null-safe location placeholder for optional config fields.
 * Used instead of org.bukkit.Location directly in config records so that
 * missing YAML keys do not trigger the core LocationSerializer's null guard.
 * When {@code world} is empty the location is considered unset.
 */
@ConfigSerializable public record LocationSpec(String world,double x,double y,double z,float yaw,float pitch){

/** Compact constructor — guards against null world produced by a missing YAML key. */
public LocationSpec{if(world==null)world="";}

/** Returns true if this spec has a non-empty world name (i.e., it was configured). */
public boolean isSet(){return!world.isEmpty();}

/**
 * Converts to a Bukkit {@link Location}.
 * Returns {@code null} when {@link #isSet()} is false.
 */
public Location toBukkit(){if(!isSet())return null;return new Location(Bukkit.getWorld(world),x,y,z,yaw,pitch);}}
