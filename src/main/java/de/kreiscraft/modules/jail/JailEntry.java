package de.kreiscraft.modules.jail;

import java.time.Instant;
import org.bukkit.Location;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Persistent record of a jailed player.
 *
 * <p>Note: {@link Instant} has no registered Configurate serializer; {@link JailData} performs
 * manual serialization of this field using epoch-millis.
 */
@ConfigSerializable public record JailEntry(Location restoreLocation,String reason,Instant jailedAt){

public JailEntry{if(reason==null)reason="";if(jailedAt==null)jailedAt=Instant.now();}}
