package de.kreiscraft.modules.bedrock;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of a known Bedrock player.
 * Stored in {@code data/bedrock-players.yml}.
 */
public record BedrockRecord(String name, UUID uuid, Instant firstSeen) {
}
