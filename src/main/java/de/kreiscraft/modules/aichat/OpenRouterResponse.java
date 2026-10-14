package de.kreiscraft.modules.aichat;

import java.math.BigDecimal;

record OpenRouterResponse(
        String content, long promptTokens, long completionTokens, long totalTokens, BigDecimal cost) {
}
