package de.kreiscraft.modules.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatusListenerTest {

    /** Non-default color, distinct from {@link Status#defaults()} (gray). */
    private static final int GOLD = 0xFFAA00;

    @Mock
    private StatusService service;

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        lenient().when(service.get(any(UUID.class))).thenReturn(Status.defaults());
        server.getPluginManager().registerEvents(new StatusListener(service, server), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String teamName(PlayerMock player) {
        return "kc_" + player.getUniqueId().toString().substring(0, 8);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void onJoin_appliesScoreboardTeam() {
        PlayerMock player = server.addPlayer("TestPlayer");
        Team team = player.getScoreboard().getTeam(teamName(player));
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(player.getName())).isTrue();
    }

    @Test
    void onJoin_appliesPrefixSuffixAndColor() {
        PlayerMock player = server.addPlayer("TestPlayer");
        Team team = player.getScoreboard().getTeam(teamName(player));
        assertThat(plain(team.prefix())).isEqualTo("Player ");
        assertThat(plain(team.suffix())).isEmpty();
        assertThat(team.color()).isEqualTo(NamedTextColor.GRAY);
    }

    @Test
    void onJoin_setsDisplayNameWithPrefixNameColourAndSuffix() {
        PlayerMock player = new PlayerMock(server, "Bob");
        Status status = new Status("<gold>Admin </gold>", "<gray> [A]</gray>", GOLD);
        when(service.get(player.getUniqueId())).thenReturn(status);

        server.addPlayer(player);

        assertThat(plain(player.displayName())).isEqualTo("Admin Bob [A]");
    }

    @Test
    void onJoin_emptyAffixesAddNoWhitespace() {
        PlayerMock player = new PlayerMock(server, "Bob");
        when(service.get(player.getUniqueId())).thenReturn(new Status("", "", GOLD));

        server.addPlayer(player);

        assertThat(plain(player.displayName())).isEqualTo("Bob");
        Team team = player.getScoreboard().getTeam(teamName(player));
        assertThat(plain(team.prefix())).isEmpty();
        assertThat(plain(team.suffix())).isEmpty();
    }

    @Test
    void onJoin_setsPlayerListNameWithStatusColourOnly() {
        PlayerMock player = new PlayerMock(server, "Bob");
        Status status = new Status("<gold>Admin</gold>", " [A]", GOLD);
        when(service.get(player.getUniqueId())).thenReturn(status);

        server.addPlayer(player);

        // The scoreboard team contributes prefix/suffix to the list entry; the list name
        // carries only the exact status colour so the two are not duplicated.
        assertThat(player.playerListName())
                .isEqualTo(Component.text("Bob").color(TextColor.color(GOLD)));
    }

    @Test
    void onJoin_reappliesIdentityToEveryOnlinePlayer() {
        Status aliceStatus = new Status("<aqua>Alice</aqua>", " [A]", 0x5555FF);
        PlayerMock alice = new PlayerMock(server, "Alice");
        when(service.get(alice.getUniqueId())).thenReturn(aliceStatus);
        server.addPlayer(alice);

        // A later join must leave the already-online player's identity intact.
        server.addPlayer("Bob");

        assertThat(plain(alice.displayName())).isEqualTo("Alice Alice [A]");
        assertThat(alice.playerListName())
                .isEqualTo(Component.text("Alice").color(TextColor.color(0x5555FF)));
    }

    @Test
    void onJoin_callsServiceGet() {
        PlayerMock player = server.addPlayer("AnotherPlayer");
        verify(service, atLeastOnce()).get(player.getUniqueId());
    }

    @Test
    void onJoin_propagatesJoiningPlayerToExistingViewers() {
        PlayerMock alice = server.addPlayer("Alice");
        Scoreboard aliceScoreboard = server.getScoreboardManager().getNewScoreboard();
        alice.setScoreboard(aliceScoreboard);

        Status bobStatus = new Status("<gold>Bob</gold>", " [B]", GOLD);
        PlayerMock bob = new PlayerMock(server, "Bob");
        when(service.get(bob.getUniqueId())).thenReturn(bobStatus);
        server.addPlayer(bob);

        Team team = aliceScoreboard.getTeam(teamName(bob));
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(bob.getName())).isTrue();
        assertThat(plain(team.prefix())).isEqualTo("Bob ");
        assertThat(plain(team.suffix())).isEqualTo(" [B]");
        assertThat(team.color()).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void onJoin_appliesExistingPlayersToJoiningPlayersScoreboard() {
        Status aliceStatus = new Status("<aqua>Alice</aqua>", " [A]", 0x5555FF);
        PlayerMock alice = new PlayerMock(server, "Alice");
        when(service.get(alice.getUniqueId())).thenReturn(aliceStatus);
        server.addPlayer(alice);

        PlayerMock bob = server.addPlayer("Bob");
        Scoreboard bobScoreboard = server.getScoreboardManager().getNewScoreboard();
        bob.setScoreboard(bobScoreboard);

        // Simulate the join refresh for a player whose scoreboard was just created.
        server.getPluginManager().callEvent(new PlayerJoinEvent(bob, Component.text("joined")));

        Team team = bobScoreboard.getTeam(teamName(alice));
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(alice.getName())).isTrue();
        assertThat(plain(team.prefix())).isEqualTo("Alice ");
        assertThat(plain(team.suffix())).isEqualTo(" [A]");
        assertThat(team.color()).isEqualTo(NamedTextColor.BLUE);
    }

    @Test
    void onStatusChanged_reappliesTeamWithUpdatedPrefixSuffixAndColor() {
        PlayerMock player = server.addPlayer("OnlinePlayer");
        String teamName = teamName(player);

        Status updated = new Status("<red>Admin</red>", " <gold>star", GOLD);
        when(service.get(player.getUniqueId())).thenReturn(updated);

        StatusChangedEvent event =
                new StatusChangedEvent(player.getUniqueId(), Status.defaults(), updated);
        server.getPluginManager().callEvent(event);

        Team team = player.getScoreboard().getTeam(teamName);
        assertThat(team).isNotNull();
        assertThat(plain(team.prefix())).isEqualTo("Admin ");
        assertThat(plain(team.suffix())).isEqualTo(" star");
        assertThat(team.color()).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void onStatusChanged_updatesDisplayNameAndPlayerListName() {
        PlayerMock player = server.addPlayer("OnlinePlayer");

        Status updated = new Status("<red>Admin</red>", " [A]", GOLD);
        when(service.get(player.getUniqueId())).thenReturn(updated);

        server.getPluginManager()
                .callEvent(new StatusChangedEvent(player.getUniqueId(), Status.defaults(), updated));

        assertThat(plain(player.displayName())).isEqualTo("Admin OnlinePlayer [A]");
        assertThat(player.playerListName())
                .isEqualTo(Component.text("OnlinePlayer").color(TextColor.color(GOLD)));
    }

    @Test
    void onStatusChanged_propagatesToEveryOnlineViewersScoreboard() {
        PlayerMock alice = server.addPlayer("Alice");
        Scoreboard aliceScoreboard = server.getScoreboardManager().getNewScoreboard();
        alice.setScoreboard(aliceScoreboard);

        PlayerMock bob = server.addPlayer("Bob");
        Scoreboard bobScoreboard = server.getScoreboardManager().getNewScoreboard();
        bob.setScoreboard(bobScoreboard);

        Status updated = new Status("<gold>Admin</gold>", " [A]", GOLD);
        when(service.get(bob.getUniqueId())).thenReturn(updated);

        server.getPluginManager()
                .callEvent(new StatusChangedEvent(bob.getUniqueId(), Status.defaults(), updated));

        for (Scoreboard scoreboard : new Scoreboard[] {aliceScoreboard, bobScoreboard}) {
            Team team = scoreboard.getTeam(teamName(bob));
            assertThat(team).isNotNull();
            assertThat(team.hasEntry(bob.getName())).isTrue();
            assertThat(plain(team.prefix())).isEqualTo("Admin ");
            assertThat(plain(team.suffix())).isEqualTo(" [A]");
            assertThat(team.color()).isEqualTo(NamedTextColor.GOLD);
        }
    }

    @Test
    void onStatusChanged_ignoresOfflinePlayer() {
        UUID offlineId = UUID.randomUUID();
        StatusChangedEvent event = new StatusChangedEvent(
                offlineId, Status.defaults(), new Status("<red>X</red>", "", GOLD));
        // Should not throw — offline players are silently skipped
        server.getPluginManager().callEvent(event);
    }
}
