package de.kreiscraft.modules.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerData(@JsonProperty("name") String name, @JsonProperty("uuid") String uuid,
        @JsonProperty("health") int health, @JsonProperty("deaths") int deaths,
        @JsonProperty("playerKills") int playerKills, @JsonProperty("playtimeSeconds") long playtimeSeconds) {
}
