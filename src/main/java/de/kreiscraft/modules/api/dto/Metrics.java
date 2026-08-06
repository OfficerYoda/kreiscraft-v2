package de.kreiscraft.modules.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Metrics(@JsonProperty("status")String status,@JsonProperty("tps")double tps,@JsonProperty("onlinePlayerCount")int onlinePlayerCount,@JsonProperty("memoryUsedMB")long memoryUsedMB,@JsonProperty("memoryMaxMB")long memoryMaxMB,@JsonProperty("cpuUsage")double cpuUsage){}
