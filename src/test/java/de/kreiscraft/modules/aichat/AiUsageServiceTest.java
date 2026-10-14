package de.kreiscraft.modules.aichat;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiUsageServiceTest {

    @TempDir
    Path temp;

    @Test
    void recordsUsageFailuresAndLatestPlayerName() throws Exception {
        AiUsageData data = new AiUsageData(temp);
        data.load();
        AiUsageService usage = new AiUsageService(data);
        UUID playerId = UUID.randomUUID();

        usage.recordSuccess(playerId, "Alice", new OpenRouterResponse(
                "hello", 10, 5, 15, new BigDecimal("0.000123")));
        usage.recordFailure(playerId, "AliceRenamed");

        assertThat(usage.find("AliceRenamed")).hasValueSatisfying(stats -> {
            assertThat(stats.successfulRequests()).isEqualTo(1);
            assertThat(stats.failedRequests()).isEqualTo(1);
            assertThat(stats.promptTokens()).isEqualTo(10);
            assertThat(stats.completionTokens()).isEqualTo(5);
            assertThat(stats.totalTokens()).isEqualTo(15);
            assertThat(stats.cost()).isEqualByComparingTo("0.000123");
        });
    }
}
