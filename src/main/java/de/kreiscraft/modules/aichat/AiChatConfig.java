package de.kreiscraft.modules.aichat;

import java.util.List;
import java.util.Objects;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record AiChatConfig(
        String apiKey, String model, String aiTrigger, int contextMsgCount, List<String> alwaysSupportedPlayers) {

    public AiChatConfig {
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "";
        }
        if (model == null || model.isBlank()) {
            model = "deepseek/deepseek-v4.1-flash";
        }
        if (aiTrigger == null || aiTrigger.isBlank()) {
            aiTrigger = "@claude";
        }
        if (contextMsgCount == 0) {
            contextMsgCount = 20;
        }
        alwaysSupportedPlayers = alwaysSupportedPlayers == null
                ? List.of()
                : alwaysSupportedPlayers.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .toList();
    }
}
