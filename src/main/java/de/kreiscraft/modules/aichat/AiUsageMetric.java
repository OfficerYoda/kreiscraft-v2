package de.kreiscraft.modules.aichat;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

enum AiUsageMetric {
    COST(Comparator.comparing(AiUsage::cost).reversed()), REQUESTS(
            Comparator.comparingLong(AiUsage::successfulRequests).reversed()), TOKENS(
                    Comparator.comparingLong(AiUsage::totalTokens).reversed());

    private final Comparator<AiUsage> comparator;

    AiUsageMetric(Comparator<AiUsage> comparator) {
        this.comparator = comparator.thenComparing(AiUsage::playerName, String.CASE_INSENSITIVE_ORDER);
    }

    Comparator<AiUsage> comparator() {
        return comparator;
    }

    static Optional<AiUsageMetric> parse(String value) {
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
