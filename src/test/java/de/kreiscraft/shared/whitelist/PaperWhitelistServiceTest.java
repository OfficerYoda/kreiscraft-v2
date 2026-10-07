package de.kreiscraft.shared.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.authlib.GameProfile;
import de.kreiscraft.platform.BedrockWhitelist;
import de.kreiscraft.platform.PaperPlatform;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class PaperWhitelistServiceTest {

    private ServerMock server;
    private PluginMock plugin;
    private BedrockWhitelist nms;
    private PaperWhitelistService svc;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        nms = mock(BedrockWhitelist.class);
        svc = new PaperWhitelistService(new PaperPlatform(plugin, server), nms);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void addJavaPlayer_usesBukkitApi() {
        PlayerMock alice = server.addPlayer("Alice");
        svc.addJavaPlayer("Alice");
        assertThat(server.getOfflinePlayer("Alice").isWhitelisted()).isTrue();
        verify(nms, times(0)).add(any());
    }

    @Test
    void addBedrockPlayer_routesToNms() {
        UUID uuid = UUID.randomUUID();
        svc.addBedrockPlayer(".steve", uuid);
        verify(nms).add(any(GameProfile.class));
    }

    @Test
    void remove_dispatchesByPrefix() {
        PlayerMock alice = server.addPlayer("Alice");
        svc.addJavaPlayer("Alice");
        svc.remove("Alice");
        assertThat(server.getOfflinePlayer("Alice").isWhitelisted()).isFalse();

        UUID bedrockUuid = UUID.randomUUID();
        when(nms.isWhitelisted(any())).thenReturn(true);
        svc.addBedrockPlayer(".steve", bedrockUuid);
        svc.remove(".steve");
        verify(nms).remove(any(GameProfile.class));
    }

    @Test
    void remove_bedrockNameWithCustomPrefix_routesToNms() {
        UUID uuid = UUID.randomUUID();
        svc.addBedrockPlayer("_steve", uuid);
        svc.remove("_steve");
        verify(nms).remove(any(GameProfile.class));
    }

    @Test
    void isWhitelisted_bedrockNameWithCustomPrefix_checksNms() {
        when(nms.isWhitelisted(any())).thenReturn(true);
        svc.addBedrockPlayer("_steve", UUID.randomUUID());
        assertThat(svc.isWhitelisted("_steve")).isTrue();
    }

    @Test
    void isWhitelisted_checksBothSides() {
        PlayerMock alice = server.addPlayer("Alice");
        svc.addJavaPlayer("Alice");
        assertThat(svc.isWhitelisted("Alice")).isTrue();
        assertThat(svc.isWhitelisted("alice")).isTrue();

        when(nms.isWhitelisted(any())).thenReturn(true);
        svc.addBedrockPlayer(".steve", UUID.randomUUID());
        assertThat(svc.isWhitelisted(".steve")).isTrue();
    }

    @Test
    void allNames_includesJavaAndBedrock() {
        PlayerMock alice = server.addPlayer("Alice");
        svc.addJavaPlayer("Alice");
        svc.addBedrockPlayer(".steve", UUID.randomUUID());
        assertThat(svc.allNames()).contains("Alice", ".steve");
    }
}
