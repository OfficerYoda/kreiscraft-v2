package de.kreiscraft.modules.reboot;

import java.time.Duration;
import java.util.List;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable public record RebootConfig(Duration defaultCountdown,String kickMessage,List<Integer>titleSeconds,List<Integer>chatSeconds){

public RebootConfig{if(defaultCountdown==null)defaultCountdown=Duration.ofSeconds(60);if(kickMessage==null)kickMessage="Server restarting";if(titleSeconds==null||titleSeconds.isEmpty())titleSeconds=List.of(500,60,30,10);if(chatSeconds==null||chatSeconds.isEmpty())chatSeconds=List.of(10,9,8,7,6,5,4,3,2,1);}}
