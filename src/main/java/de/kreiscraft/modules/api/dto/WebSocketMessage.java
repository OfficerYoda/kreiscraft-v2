package de.kreiscraft.modules.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WebSocketMessage(@JsonProperty("type") WebSocketMessageType type,
        @JsonProperty("content") String content) {
}
