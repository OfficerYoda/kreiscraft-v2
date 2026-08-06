package de.kreiscraft.modules.updatemode;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable public record UpdateModeConfig(String bypassPermission,List<String>bypassNames,Component kickMessage){

public UpdateModeConfig{if(bypassPermission==null)bypassPermission="kreiscraft.updatemode.bypass";if(bypassNames==null)bypassNames=List.of();if(kickMessage==null)kickMessage=MiniMessage.miniMessage().deserialize("<red>Server im Wartungsmodus</red>");}}
