package de.kreiscraft.modules.aichat;

import de.kreiscraft.core.config.DataFile;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

final class AiUsageData extends DataFile<Map<UUID, AiUsage>> {

    AiUsageData(Path dataFolder) {
        super(dataFolder, "data/ai-chat-usage.yml");
    }

    @Override
    protected Map<UUID, AiUsage> load(ConfigurationNode node) throws SerializationException {
        Map<UUID, AiUsage> usages = new ConcurrentHashMap<>();
        for (var entry : node.childrenMap().entrySet()) {
            UUID playerId = UUID.fromString(String.valueOf(entry.getKey()));
            ConfigurationNode value = entry.getValue();
            usages.put(playerId, new AiUsage(
                    value.node("player-name").getString("Unknown"),
                    value.node("successful-requests").getLong(),
                    value.node("failed-requests").getLong(),
                    value.node("prompt-tokens").getLong(),
                    value.node("completion-tokens").getLong(),
                    value.node("total-tokens").getLong(),
                    value.node("cost").get(BigDecimal.class, BigDecimal.ZERO)));
        }
        return usages;
    }

    @Override
    protected void save(Map<UUID, AiUsage> usages, ConfigurationNode node) throws SerializationException {
        node.set(null);
        for (var entry : usages.entrySet()) {
            AiUsage usage = entry.getValue();
            ConfigurationNode value = node.node(entry.getKey().toString());
            value.node("player-name").set(usage.playerName());
            value.node("successful-requests").set(usage.successfulRequests());
            value.node("failed-requests").set(usage.failedRequests());
            value.node("prompt-tokens").set(usage.promptTokens());
            value.node("completion-tokens").set(usage.completionTokens());
            value.node("total-tokens").set(usage.totalTokens());
            value.node("cost").set(usage.cost());
        }
    }

    @Override
    protected Map<UUID, AiUsage> empty() {
        return new ConcurrentHashMap<>();
    }
}
