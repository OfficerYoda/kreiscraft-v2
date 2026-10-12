package de.kreiscraft.modules.aichat;

import java.math.BigDecimal;

record AiUsage(
        String playerName,
        long successfulRequests,
        long failedRequests,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal cost) {

    static AiUsage empty(String playerName) {
        return new AiUsage(playerName, 0, 0, 0, 0, 0, BigDecimal.ZERO);
    }

    AiUsage successful(String playerName, OpenRouterResponse response) {
        return new AiUsage(
                playerName,
                successfulRequests + 1,
                failedRequests,
                promptTokens + response.promptTokens(),
                completionTokens + response.completionTokens(),
                totalTokens + response.totalTokens(),
                cost.add(response.cost()));
    }

    AiUsage failed(String playerName) {
        return new AiUsage(
                playerName, successfulRequests, failedRequests + 1, promptTokens, completionTokens, totalTokens, cost);
    }
}
