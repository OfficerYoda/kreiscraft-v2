package de.kreiscraft.modules.aichat;

public interface AiChatService {

    void chatMessage(MinecraftChatMessage message);

    void aiRequest(String requestMsg);
}
