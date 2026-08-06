package de.kreiscraft.modules.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonRoundTripTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void metrics_roundTrips() throws Exception {
        var original = new Metrics("Online", 19.98, 3, 512, 2048, 14.3);
        var json = mapper.writeValueAsString(original);
        assertThat(json).contains("\"status\":\"Online\"");
        assertThat(mapper.readValue(json, Metrics.class)).isEqualTo(original);
    }

    @Test
    void playerData_roundTrips() throws Exception {
        var original = new PlayerData("Alice", "550e8400-e29b-41d4-a716-446655440000", 20, 5, 2, 3600);
        assertThat(mapper.readValue(mapper.writeValueAsString(original), PlayerData.class)).isEqualTo(original);
    }

    @Test
    void linkedPlayerResponse_omitsNullPlayerData() throws Exception {
        var pending = LinkedPlayerResponse.pending();
        var json = mapper.writeValueAsString(pending);
        assertThat(json).doesNotContain("playerData");
        assertThat(json).contains("\"status\":\"pending\"");
    }

    @Test
    void linkedPlayerResponse_includesPlayerDataWhenPresent() throws Exception {
        var pd = new PlayerData("Alice", "u", 20, 0, 0, 0);
        var accepted = LinkedPlayerResponse.accepted(pd);
        var json = mapper.writeValueAsString(accepted);
        assertThat(json).contains("\"playerData\"");
    }

    @Test
    void webSocketMessage_roundTrips() throws Exception {
        var original = new WebSocketMessage(WebSocketMessageType.CHAT, "hello");
        assertThat(mapper.readValue(mapper.writeValueAsString(original), WebSocketMessage.class)).isEqualTo(original);
    }

    @Test
    void webSocketMessageType_serializesAsBareEnumName() throws Exception {
        String json = mapper.writeValueAsString(WebSocketMessageType.PING);
        assertThat(json).isEqualTo("\"PING\"");
    }

    @Test
    void initializationData_roundTrips() throws Exception {
        var original = new InitializationData("new", "u");
        assertThat(mapper.readValue(mapper.writeValueAsString(original), InitializationData.class)).isEqualTo(original);
    }

    @Test
    void linkRequest_deserializesFromClientJson() throws Exception {
        var parsed = mapper.readValue("{\"username\":\"Alice\"}", LinkRequest.class);
        assertThat(parsed.username()).isEqualTo("Alice");
    }

    @Test
    void whitelistRequest_deserializesFromClientJson() throws Exception {
        var parsed = mapper.readValue("{\"playerName\":\"Alice\"}", WhitelistRequest.class);
        assertThat(parsed.playerName()).isEqualTo("Alice");
    }
}
