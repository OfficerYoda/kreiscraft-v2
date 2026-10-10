package de.kreiscraft.modules.anticheat.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionTestSupport;
import de.kreiscraft.core.message.ConfigurateMessageService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.anticheat.DefaultHackLogService;
import de.kreiscraft.modules.anticheat.HackEntry;
import de.kreiscraft.modules.anticheat.HackLogData;
import de.kreiscraft.modules.anticheat.HackLogService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HackerLogsCommandTest {

    @TempDir
    Path tmp;

    private ServerMock server;

    @Mock
    MessageService messages;

    @Mock
    HackLogService commandService;

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
        assertThat(HackerLogsCommand.build(service, messages, server)).isNotNull();
    }

    @Test
    void build_nodeName_isHackerlogs() {
        assertThat(HackerLogsCommand.build(service, messages, server).getName())
                .isEqualTo("hackerlogs");
    }

    @Test
    void permissionGate_senderWithoutPermission_requiresFails() {
        var node = HackerLogsCommand.build(service, messages, server);
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
        var node = HackerLogsCommand.build(service, messages, server);
        CommandSender withPerms = mock(CommandSender.class);
        when(withPerms.hasPermission("kreiscraft.anticheat.view")).thenReturn(true);

        io.papermc.paper.command.brigadier.CommandSourceStack src =
                mock(io.papermc.paper.command.brigadier.CommandSourceStack.class);
        when(src.getSender()).thenReturn(withPerms);

        assertThat(node.getRequirement().test(src)).isTrue();
    }

    // -----------------------------------------------------------------------
    // Selector cardinality
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void execute_multiPlayerSelector_showsLogsForEverySelectedPlayer() throws Exception {
        LiteralCommandNode<CommandSourceStack> node =
                HackerLogsCommand.build(commandService, messages, server);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        HackEntry entry = new HackEntry(
                UUID.randomUUID(), "Bob", HackEntry.Kind.CHEAT, "killaura", Instant.now());
        when(commandService.forPlayer(any(), eq(HackEntry.Kind.CHEAT))).thenReturn(List.of(entry));
        when(commandService.forPlayer(any(), eq(HackEntry.Kind.XRAY))).thenReturn(List.of());
        when(messages.renderFor(any(), eq("anti-cheat.kind-cheats")))
                .thenReturn(Component.text("cheats"));

        CommandSender sender = mock(CommandSender.class);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(sender);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        PlayerSelectorArgumentResolver resolver = mock(PlayerSelectorArgumentResolver.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", PlayerSelectorArgumentResolver.class)).thenReturn(resolver);
        when(resolver.resolve(src)).thenReturn(List.of(alice, bob));

        node.getChild("target").getCommand().run(ctx);

        verify(messages)
                .send(eq(sender), eq("anti-cheat.log-header"),
                        eq("player"), eq("Alice"), eq("kind"), eq("cheats"));
        verify(messages)
                .send(eq(sender), eq("anti-cheat.log-header"),
                        eq("player"), eq("Bob"), eq("kind"), eq("cheats"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_multiPlayerSelector_withoutLogs_notifiesForEveryPlayer() throws Exception {
        LiteralCommandNode<CommandSourceStack> node =
                HackerLogsCommand.build(commandService, messages, server);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        when(commandService.forPlayer(any(), any())).thenReturn(List.of());

        CommandSender sender = mock(CommandSender.class);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(sender);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        PlayerSelectorArgumentResolver resolver = mock(PlayerSelectorArgumentResolver.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", PlayerSelectorArgumentResolver.class)).thenReturn(resolver);
        when(resolver.resolve(src)).thenReturn(List.of(alice, bob));

        node.getChild("target").getCommand().run(ctx);

        verify(messages).send(sender, "anti-cheat.no-logs", "player", "Alice");
        verify(messages).send(sender, "anti-cheat.no-logs", "player", "Bob");
    }

    // -----------------------------------------------------------------------
    // Suggestions
    // -----------------------------------------------------------------------

    @Test
    void targetArgument_suggestsOnlineAndKnownOfflineProfiles() {
        Server suggestionServer = mock(Server.class);
        List<org.bukkit.entity.Player> online = new java.util.ArrayList<>();
        online.add(server.addPlayer("Alice"));
        when(suggestionServer.getOnlinePlayers()).thenAnswer(invocation -> online);
        OfflinePlayer carol = mock(OfflinePlayer.class);
        when(carol.hasPlayedBefore()).thenReturn(true);
        when(carol.getName()).thenReturn("Carol");
        when(suggestionServer.getOfflinePlayers()).thenReturn(new OfflinePlayer[] {carol});
        LiteralCommandNode<CommandSourceStack> node =
                HackerLogsCommand.build(commandService, messages, suggestionServer);

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("kreiscraft.anticheat.view")).thenReturn(true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(sender);

        assertThat(SuggestionTestSupport.suggestions(node.getChild("target"), src, ""))
                .containsExactlyInAnyOrder("Alice", "Carol");
    }

    // -----------------------------------------------------------------------
    // Localization
    // -----------------------------------------------------------------------

    @Test
    void showLogs_rendersHeaderAndKindLabelInRecipientLanguage(@TempDir Path langDir)
            throws IOException {
        ConfigurateMessageService localized = new ConfigurateMessageService(
                langDir,
                getClass().getClassLoader(),
                "de",
                server,
                recipient -> "Alice".equals(recipient.getName())
                        ? Optional.of("en")
                        : Optional.empty());
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock carol = server.addPlayer("Carol");
        UUID bobId = UUID.randomUUID();
        when(commandService.forPlayer(bobId, HackEntry.Kind.CHEAT))
                .thenReturn(List.of(new HackEntry(
                        bobId, "Bob", HackEntry.Kind.CHEAT, "killaura", Instant.now())));
        when(commandService.forPlayer(bobId, HackEntry.Kind.XRAY)).thenReturn(List.of());

        HackerLogsCommand.showLogs(alice, bobId, "Bob", null, commandService, localized);
        HackerLogsCommand.showLogs(carol, bobId, "Bob", null, commandService, localized);

        assertThat(alice.nextMessage())
                .as("Alice stored English")
                .contains("Entries for")
                .contains("Bob")
                .contains("cheats");
        assertThat(carol.nextMessage())
                .as("Carol stored no preference and must receive the server language")
                .contains("Einträge für")
                .contains("Bob")
                .contains("Cheats");
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
