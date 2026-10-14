package de.kreiscraft.modules.aichat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class AiUsageService {

    private final AiUsageData data;

    AiUsageService(AiUsageData data) {
        this.data = data;
    }

    synchronized void recordSuccess(UUID playerId, String playerName, OpenRouterResponse response) {
        data.update(usages -> usages.compute(
                playerId,
                (ignored, usage) -> (usage == null ? AiUsage.empty(playerName) : usage)
                        .successful(playerName, response)));
    }

    synchronized void recordFailure(UUID playerId, String playerName) {
        data.update(usages -> usages.compute(
                playerId,
                (ignored, usage) -> (usage == null ? AiUsage.empty(playerName) : usage).failed(playerName)));
    }

    synchronized Optional<AiUsage> find(String playerName) {
        return data.get().values().stream()
                .filter(usage -> usage.playerName().equalsIgnoreCase(playerName))
                .findFirst();
    }

    synchronized AiUsage total() {
        return data.get().values().stream().reduce(AiUsage.empty("all"), AiUsageService::add);
    }

    synchronized List<AiUsage> top(AiUsageMetric metric) {
        return data.get().values().stream().sorted(metric.comparator()).toList();
    }

    synchronized List<String> playerNames() {
        return data.get().values().stream().map(AiUsage::playerName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static AiUsage add(AiUsage left, AiUsage right) {
        return new AiUsage(
                "all",
                left.successfulRequests() + right.successfulRequests(),
                left.failedRequests() + right.failedRequests(),
                left.promptTokens() + right.promptTokens(),
                left.completionTokens() + right.completionTokens(),
                left.totalTokens() + right.totalTokens(),
                left.cost().add(right.cost()));
    }
}
