package de.kreiscraft.shared.chat;

import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Immutable envelope for a chat line published on {@link ChatBridge}.
 *
 * @param source origin of this chat line
 * @param senderUuid UUID of the sending player, or {@code null} for non-player messages
 * @param senderName display name at time of send (raw, no formatting)
 * @param senderColor player-name colour at time of send as {@code #rrggbb}, or {@code null} for non-player messages
 * @param rendered the fully rendered Adventure component broadcast in-game
 * @param plainText pre-computed plain-text serialization for external consumers
 * @param timestamp when this line was produced
 */
public record ChatBridgeEvent(ChatSource source, UUID senderUuid, String senderName, String senderColor,
        Component rendered,
        String plainText, Instant timestamp) {
}
