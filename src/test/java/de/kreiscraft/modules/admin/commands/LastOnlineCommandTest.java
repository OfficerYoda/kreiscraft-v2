package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.modules.admin.CapturingMessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class LastOnlineCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private CapturingMessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = new CapturingMessageService();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static PlaceholderResolver resolver(ServerMock server, String zone, Instant now) {
        return new PlaceholderResolver(
                server, ZoneId.of(zone), Clock.fixed(now, ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private static CommandContext<CommandSourceStack> contextFor(PlayerMock executor, String name) {
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(executor);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("player", String.class)).thenReturn(name);
        return ctx;
    }

    @Test
    void build_requiresPermission_whenMissing_returnsFalse() {
        LiteralCommandNode<CommandSourceStack> node = LastOnlineCommand.build(
                messages,
                server,
                resolver(server, "Europe/Berlin", Instant.parse("2026-01-15T12:34:56Z")));
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_whenGranted_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node = LastOnlineCommand.build(
                messages,
                server,
                resolver(server, "Europe/Berlin", Instant.parse("2026-01-15T12:34:56Z")));
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.lastonline", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @Test
    void execute_showsBerlinAbsoluteTimeAndLocalizedRelativeDuration() throws Exception {
        Instant now = Instant.parse("2026-01-15T12:34:56Z");
        LiteralCommandNode<CommandSourceStack> node =
                LastOnlineCommand.build(messages, server, resolver(server, "Europe/Berlin", now));
        OfflinePlayer target = server.getOfflinePlayer("Bob");
        // Two hours before the fixed "now"; Europe/Berlin is UTC+1 in January.
        server.getPlayerList()
                .setLastSeen(target.getUniqueId(), now.minusSeconds(2 * 3600).toEpochMilli());
        PlayerMock executor = server.addPlayer("Admin");

        node.getChild("player").getCommand().run(contextFor(executor, "Bob"));

        assertThat(messages.sentKeys).containsExactly("admin.lastonline");
        Object[] placeholders = messages.sentPlaceholders.get(0);
        assertThat(placeholders[0]).isEqualTo("player");
        assertThat(placeholders[1]).isEqualTo("Bob");
        assertThat((String) placeholders[3])
                .as("absolute time must be rendered in Europe/Berlin")
                .contains("11:34:56");
        Component relative = (Component) placeholders[5];
        assertThat(PlainTextComponentSerializer.plainText().serialize(relative))
                .as("relative duration must be rendered from the recipient-language key")
                .isEqualTo("time.relative.hours");
        assertThat(messages.renderedKeys).containsExactly("time.relative.hours");
    }

    @Test
    void execute_neverOnline_sendsNeverKey() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = LastOnlineCommand.build(
                messages,
                server,
                resolver(server, "Europe/Berlin", Instant.parse("2026-01-15T12:34:56Z")));
        // A profile that has never been seen has no stored last-seen timestamp.
        server.getOfflinePlayer("Ghost");
        PlayerMock executor = server.addPlayer("Admin");

        node.getChild("player").getCommand().run(contextFor(executor, "Ghost"));

        assertThat(messages.sentKeys).containsExactly("admin.lastonline-never");
    }

    @Test
    void relativeSince_mapsElapsedTimeOntoBuckets() {
        Instant now = Instant.parse("2026-01-15T12:34:56Z");

        assertThat(LastOnlineCommand.relativeSince(now.minusSeconds(2), now).bucket())
                .isEqualTo("just-now");
        assertThat(LastOnlineCommand.relativeSince(now.minusSeconds(45), now))
                .isEqualTo(new LastOnlineCommand.Relative("seconds", 45));
        assertThat(LastOnlineCommand.relativeSince(now.minusSeconds(45 * 60), now))
                .isEqualTo(new LastOnlineCommand.Relative("minutes", 45));
        assertThat(LastOnlineCommand.relativeSince(now.minusSeconds(90 * 60), now))
                .isEqualTo(new LastOnlineCommand.Relative("hours", 1));
        assertThat(LastOnlineCommand.relativeSince(now.minusSeconds(5 * 3600), now))
                .isEqualTo(new LastOnlineCommand.Relative("hours", 5));
        assertThat(LastOnlineCommand.relativeSince(now.minusSeconds(3 * 24 * 3600), now))
                .isEqualTo(new LastOnlineCommand.Relative("days", 3));
        assertThat(LastOnlineCommand.relativeSince(now.plusSeconds(30), now).bucket())
                .as("a future timestamp must clamp to just now")
                .isEqualTo("just-now");
    }

    @Test
    void build_wiresOnlineAndKnownOfflineProfileSuggestions() {
        LiteralCommandNode<CommandSourceStack> node = LastOnlineCommand.build(
                messages,
                server,
                resolver(server, "Europe/Berlin", Instant.parse("2026-01-15T12:34:56Z")));
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "kreiscraft.admin.lastonline", true);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(alice);
        assertThat(
                de.kreiscraft.core.command.SuggestionTestSupport.suggestions(
                        node.getChild("player"), src, "Al"))
                .contains("Alice");
    }
}
