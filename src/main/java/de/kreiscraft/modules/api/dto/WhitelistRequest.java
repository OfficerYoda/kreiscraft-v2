package de.kreiscraft.modules.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WhitelistRequest(@JsonProperty("playerName")String playerName){}
