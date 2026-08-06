package de.kreiscraft.modules.bedrock;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable public record BedrockConfig(String prefix){

/** Compact constructor — supplies defaults when config keys are absent. */
public BedrockConfig{if(prefix==null)prefix=".";}}
