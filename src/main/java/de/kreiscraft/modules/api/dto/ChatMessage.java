package de.kreiscraft.modules.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(WebSocketMessageType type, String content, String source, String playerName,
        String playerColor) {
}
