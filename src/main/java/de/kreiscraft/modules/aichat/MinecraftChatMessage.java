package de.kreiscraft.modules.aichat;

import java.time.Instant;
import java.util.UUID;
import de.kreiscraft.shared.chat.ChatSource;

record MinecraftChatMessage(Instant timestamp, ChatSource source, String senderName, String content, UUID senderId) {

    MinecraftChatMessage(Instant timestamp, ChatSource source, String senderName, String content) {
        this(timestamp, source, senderName, content, null);
    }
}
