package de.kreiscraft.modules.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InitializationData(@JsonProperty("status") String status, // "new" | "existing"
        @JsonProperty("userId") String userId) {
}
