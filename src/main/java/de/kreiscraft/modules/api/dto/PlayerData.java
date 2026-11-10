package de.kreiscraft.modules.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PlayerData(@JsonProperty("name") String name, @JsonProperty("uuid") String uuid,
        @JsonProperty("health") int health, @JsonProperty("maxHealth") int maxHealth,
        @JsonProperty("absorption") double absorption, @JsonProperty("effects") List<String> effects,
        @JsonProperty("deaths") int deaths,
        @JsonProperty("playerKills") int playerKills, @JsonProperty("playtimeSeconds") long playtimeSeconds) {
}
