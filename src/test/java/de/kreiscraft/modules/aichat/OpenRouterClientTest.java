package de.kreiscraft.modules.aichat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenRouterClientTest {

    @Test
    void extractsContentAndProviderReportedUsage() throws Exception {
        OpenRouterResponse response = OpenRouterClient.extractResponse("""
                {
                  "choices": [{"message": {"content": "Howdy!"}}],
                  "usage": {
                    "prompt_tokens": 262,
                    "completion_tokens": 51,
                    "total_tokens": 313,
                    "cost": 0.00007689
                  }
                }
                """);

        assertThat(response.content()).isEqualTo("Howdy!");
        assertThat(response.promptTokens()).isEqualTo(262);
        assertThat(response.completionTokens()).isEqualTo(51);
        assertThat(response.totalTokens()).isEqualTo(313);
        assertThat(response.cost()).isEqualByComparingTo("0.00007689");
    }
}
