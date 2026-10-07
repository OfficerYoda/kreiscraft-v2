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
import java.time.ZoneId;
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

    private static PlaceholderResolver resolver(ServerMock server, String zone) {
        return new PlaceholderResolver(server, ZoneId.of(zone), Clock.systemUTC());
    }

    @Test
    void build_requiresPermission_whenMissing_returnsFalse() {
        LiteralCommandNode<CommandSourceStack> node = LastOnlineCommand.build(
                messages, server, resolver(server, "Europe/Berlin"));
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_whenGranted_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node = LastOnlineCommand.build(
                messages, server, resolver(server, "Europe/Berlin"));
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.lastonline", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_sendsLastOnlineKey() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = LastOnlineCommand.build(
                messages, server, resolver(server, "Europe/Berlin"));
        PlayerMock target = server.addPlayer("Bob");
        PlayerMock executor = server.addPlayer("Admin");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(executor);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("player", String.class)).thenReturn("Bob");

        node.getChild("player").getCommand().run(ctx);

        assertThat(messages.sentKeys).containsExactly("admin.lastonline");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_usesCanonicalCoreTimezone() throws Exception {
        // UTC+9 is a fixed-offset zone (no DST) so the offset is deterministic. The command reads
        // the canonical core timezone from PlaceholderResolver, not an admin-module setting.
        LiteralCommandNode<CommandSourceStack> node =
                LastOnlineCommand.build(messages, server, resolver(server, "UTC+9"));
        server.addPlayer("Bob");
        PlayerMock executor = server.addPlayer("Admin");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(executor);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("player", String.class)).thenReturn("Bob");

        node.getChild("player").getCommand().run(ctx);

        // "when" is at placeholder index 3 (placeholders = ["player","Bob","when","<timestamp>"])
        String when = (String) messages.sentPlaceholders.get(0)[3];
        assertThat(when).endsWith("+09:00");
    }
}
