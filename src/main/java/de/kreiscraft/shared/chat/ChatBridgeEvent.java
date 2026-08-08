package de.kreiscraft.shared.chat;

import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Immutable envelope for a chat line published on {@link ChatBridge}. The chat-format module (Plan
 * 3) publishes one of these for every rendered chat line; the API module (Plan 6) subscribes to
 * forward them to connected WebSocket clients.
 *
 * @param senderUuid UUID of the sending player, or {@code null} for console/system messages
 * @param senderName display name at time of send (raw, no formatting)
 * @param rendered the fully rendered Adventure component broadcast in-game
 * @param plainText pre-computed plain-text serialization for external consumers
 * @param timestamp when this line was produced
 */
public record ChatBridgeEvent(UUID senderUuid, String senderName, Component rendered, String plainText,
        Instant timestamp) {
}
