package de.kreiscraft.modules.aichat;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record AiChatConfig(String apiKey, String model, String chatMentionTag, int contextMsgCount) {

    public AiChatConfig {
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "";
        }
        if (model == null || model.isBlank()) {
            model = "deepseek/deepseek-v4.1-flash";
        }
        if (chatMentionTag == null || chatMentionTag.isBlank()) {
            chatMentionTag = "@claude";
        }
        if (contextMsgCount == 0) {
            contextMsgCount = 20;
        }
    }
}
