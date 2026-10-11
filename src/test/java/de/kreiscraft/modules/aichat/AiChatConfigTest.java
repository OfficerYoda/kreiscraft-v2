package de.kreiscraft.modules.aichat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiChatConfigTest {

    @Test
    void alwaysSupportedPlayers_defaultsToAnEmptyList() {
        AiChatConfig config = new AiChatConfig("key", "model", "prompt", "response", "@claude", 20, null);

        assertThat(config.alwaysSupportedPlayers()).isEmpty();
    }

    @Test
    void alwaysSupportedPlayers_preservesConfiguredNames() {
        AiChatConfig config =
                new AiChatConfig("key", "model", "prompt", "response", "@claude", 20, List.of("SteveGamer42"));

        assertThat(config.alwaysSupportedPlayers()).containsExactly("SteveGamer42");
    }

    @Test
    void systemPrompt_defaultsToTheLegacyPromptForExistingConfigurations() {
        AiChatConfig config = new AiChatConfig("key", "model", null, "response", "@claude", 20, null);

        assertThat(config.systemPrompt()).isEqualTo(AiChatConfig.DEFAULT_SYSTEM_PROMPT);
    }

    @Test
    void responseFormat_defaultsToTheLegacyFormatForExistingConfigurations() {
        AiChatConfig config = new AiChatConfig("key", "model", "prompt", null, "@claude", 20, null);

        assertThat(config.responseFormat()).isEqualTo(AiChatConfig.DEFAULT_RESPONSE_FORMAT);
    }
}
