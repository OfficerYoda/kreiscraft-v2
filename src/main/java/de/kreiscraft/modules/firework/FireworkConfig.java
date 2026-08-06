package de.kreiscraft.modules.firework;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable public record FireworkConfig(int customModelData,String namespaceKey){

public FireworkConfig{if(namespaceKey==null)namespaceKey="kreiscraft:firework_rocket";if(customModelData==0)customModelData=696969;}}
