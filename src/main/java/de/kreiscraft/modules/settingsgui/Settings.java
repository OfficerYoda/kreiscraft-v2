package de.kreiscraft.modules.settingsgui;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable public record Settings(@Setting("click-through")boolean clickThrough,@Setting("combat-display")CombatDisplay combatDisplay){

public static Settings defaults(){return new Settings(true,CombatDisplay.ACTIONBAR);}}
