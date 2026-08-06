package de.kreiscraft.modules.tablist;

import java.time.Duration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable public record TablistConfig(Duration updateInterval,String header,String footer){

public TablistConfig{if(updateInterval==null)updateInterval=Duration.ofMillis(20*50L); // 20 ticks = 1 s
if(header==null)header="<gradient:#5555ff:#55ffff>Kreiscraft</gradient>";if(footer==null)footer="TPS: %tps% | Players: %server_players%";}}
