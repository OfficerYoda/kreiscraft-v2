package de.kreiscraft.modules.positions;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable public record PositionsConfig(int guiRows,boolean teleportOnClick){

public PositionsConfig{
// Default guiRows to 6 when missing/zero from config
if(guiRows<=0)guiRows=6;}}
