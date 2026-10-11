package de.kreiscraft.modules.aichat;

import java.time.Instant;
import de.kreiscraft.shared.chat.ChatSource;

record MinecraftChatMessage(Instant timestamp, ChatSource source, String senderName, String content) {
}
