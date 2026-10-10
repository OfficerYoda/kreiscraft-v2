package de.kreiscraft.modules.anticheat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModCheckerChannelTest {

    @TempDir
    Path tmp;

    @Mock
    MessageService messages;

    @Mock
    Player player;

    private DefaultHackLogService service;
    private ModCheckerChannel channel;
    private UUID playerId;

    @BeforeEach
    void setUp() throws IOException {
        HackLogData data = new HackLogData(tmp);
        data.load();
        service = new DefaultHackLogService(data);
        channel = new ModCheckerChannel(service, messages);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("TestPlayer");
        when(messages.render(any(), any())).thenReturn(Component.empty());
        when(messages.renderFor(any(), any(), any())).thenReturn(Component.empty());
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void cheats_recordsEntryAndKicksPlayer() {
        when(messages.renderFor(eq(player), eq("anti-cheat.cheat-kick"), eq("detail"), eq("killaura")))
                .thenReturn(Component.text("kick"));

        channel.onPluginMessageReceived("kreiscraft:mod_checker", player, bytes("cheats:killaura"));

        assertThat(service.forPlayer(playerId, HackEntry.Kind.CHEAT))
                .hasSize(1)
                .extracting(HackEntry::detail)
                .containsExactly("killaura");
        verify(player).kick(Component.text("kick"));
    }

    @Test
    void xray_recordsEntryAndKicksPlayer() {
        when(messages.renderFor(eq(player), eq("anti-cheat.xray-kick"), eq("detail"), eq("diamond_ore")))
                .thenReturn(Component.text("xray-kick"));

        channel.onPluginMessageReceived("kreiscraft:mod_checker", player, bytes("xray:diamond_ore"));

        assertThat(service.forPlayer(playerId, HackEntry.Kind.XRAY))
                .hasSize(1)
                .extracting(HackEntry::detail)
                .containsExactly("diamond_ore");
        verify(player).kick(Component.text("xray-kick"));
    }

    @Test
    void freecam_false_marksHandshakePassed() {
        assertThat(service.hasHandshakePassed(playerId)).isFalse();
        channel.onPluginMessageReceived("kreiscraft:mod_checker", player, bytes("freecam:false"));
        assertThat(service.hasHandshakePassed(playerId)).isTrue();
    }

    @Test
    void freecam_true_kicksPlayer() {
        when(messages.renderFor(eq(player), eq("anti-cheat.freecam-kick")))
                .thenReturn(Component.text("freecam-kick"));

        channel.onPluginMessageReceived("kreiscraft:mod_checker", player, bytes("freecam:true"));

        verify(player).kick(Component.text("freecam-kick"));
        assertThat(service.hasHandshakePassed(playerId)).isFalse();
    }

    @Test
    void malformed_noColon_doesNotThrow() {
        // must not throw, must not kick
        channel.onPluginMessageReceived("kreiscraft:mod_checker", player, bytes("malformed_no_colon"));
        verify(player, never()).kick(any(Component.class));
    }

    @Test
    void malformed_emptyValue_doesNotThrow() {
        // type: with nothing after colon
        channel.onPluginMessageReceived("kreiscraft:mod_checker", player, bytes("cheats:"));
        verify(player, never()).kick(any(Component.class));
        assertThat(service.forPlayer(playerId, HackEntry.Kind.CHEAT)).isEmpty();
    }

    @Test
    void malformed_emptyPayload_doesNotThrow() {
        channel.onPluginMessageReceived("kreiscraft:mod_checker", player, bytes(""));
        verify(player, never()).kick(any(Component.class));
    }

    @Test
    void unknown_type_isIgnored() {
        channel.onPluginMessageReceived("kreiscraft:mod_checker", player, bytes("unknown:value"));
        verify(player, never()).kick(any(Component.class));
    }
}
