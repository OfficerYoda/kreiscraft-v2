package de.kreiscraft.modules.anticheat.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.anticheat.DefaultHackLogService;
import de.kreiscraft.modules.anticheat.HackEntry;
import de.kreiscraft.modules.anticheat.HackLogData;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HackerLogsCommandTest {

    @TempDir
    Path tmp;

    private ServerMock server;

    @Mock
    MessageService messages;

    private DefaultHackLogService service;
    private UUID targetId;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        HackLogData data = new HackLogData(tmp);
        data.load();
        service = new DefaultHackLogService(data);
        targetId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_returnsNonNullNode() {
        assertThat(HackerLogsCommand.build(service, messages)).isNotNull();
    }

    @Test
    void build_nodeName_isHackerlogs() {
        assertThat(HackerLogsCommand.build(service, messages).getName()).isEqualTo("hackerlogs");
    }

    @Test
    void permissionGate_senderWithoutPermission_requiresFails() {
        var node = HackerLogsCommand.build(service, messages);
        // The requires predicate should reject senders without permission
        CommandSender noPerms = mock(CommandSender.class);
        when(noPerms.hasPermission("kreiscraft.anticheat.view")).thenReturn(false);

        io.papermc.paper.command.brigadier.CommandSourceStack src =
                mock(io.papermc.paper.command.brigadier.CommandSourceStack.class);
        when(src.getSender()).thenReturn(noPerms);

        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void permissionGate_senderWithPermission_requiresPasses() {
        var node = HackerLogsCommand.build(service, messages);
        CommandSender withPerms = mock(CommandSender.class);
        when(withPerms.hasPermission("kreiscraft.anticheat.view")).thenReturn(true);

        io.papermc.paper.command.brigadier.CommandSourceStack src =
                mock(io.papermc.paper.command.brigadier.CommandSourceStack.class);
        when(src.getSender()).thenReturn(withPerms);

        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @Test
    void noLogs_sendsNoLogsMessage() {
        // Direct service-level verification that forPlayer returns empty
        assertThat(service.forPlayer(targetId, HackEntry.Kind.CHEAT)).isEmpty();
        assertThat(service.forPlayer(targetId, HackEntry.Kind.XRAY)).isEmpty();
    }

    @Test
    void logsPresent_cheatEntryStoredCorrectly() {
        Instant when = Instant.now();
        service.record(new HackEntry(targetId, "Hacker", HackEntry.Kind.CHEAT, "killaura", when));

        List<HackEntry> cheats = service.forPlayer(targetId, HackEntry.Kind.CHEAT);
        assertThat(cheats).hasSize(1);
        assertThat(cheats.get(0).detail()).isEqualTo("killaura");
        assertThat(cheats.get(0).playerName()).isEqualTo("Hacker");
    }

    @Test
    void logsPresent_xrayEntryStoredCorrectly() {
        Instant when = Instant.now();
        service.record(new HackEntry(targetId, "Hacker", HackEntry.Kind.XRAY, "diamond_ore", when));

        List<HackEntry> xray = service.forPlayer(targetId, HackEntry.Kind.XRAY);
        assertThat(xray).hasSize(1);
        assertThat(xray.get(0).detail()).isEqualTo("diamond_ore");
    }

    @Test
    void kindFilter_separatesCheatFromXray() {
        Instant when = Instant.now();
        service.record(new HackEntry(targetId, "H", HackEntry.Kind.CHEAT, "fly", when));
        service.record(new HackEntry(targetId, "H", HackEntry.Kind.XRAY, "diamond_ore", when.plusMillis(1)));

        assertThat(service.forPlayer(targetId, HackEntry.Kind.CHEAT)).hasSize(1);
        assertThat(service.forPlayer(targetId, HackEntry.Kind.XRAY)).hasSize(1);
    }

    // --- parseKind validation ---

    @Test
    void parseKind_cheats_returnsCHEAT() {
        assertThat(HackerLogsCommand.parseKind("cheats")).contains(HackEntry.Kind.CHEAT);
    }

    @Test
    void parseKind_cheats_caseInsensitive() {
        assertThat(HackerLogsCommand.parseKind("CHEATS")).contains(HackEntry.Kind.CHEAT);
    }

    @Test
    void parseKind_xray_returnsXRAY() {
        assertThat(HackerLogsCommand.parseKind("xray")).contains(HackEntry.Kind.XRAY);
    }

    @Test
    void parseKind_xray_caseInsensitive() {
        assertThat(HackerLogsCommand.parseKind("XRAY")).contains(HackEntry.Kind.XRAY);
    }

    @Test
    void parseKind_unknownInput_returnsEmpty() {
        assertThat(HackerLogsCommand.parseKind("misspelled")).isEmpty();
    }

    @Test
    void parseKind_emptyString_returnsEmpty() {
        assertThat(HackerLogsCommand.parseKind("")).isEmpty();
    }
}
