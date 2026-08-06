package de.kreiscraft.modules.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LinkRequest(@JsonProperty("username")String username){}
