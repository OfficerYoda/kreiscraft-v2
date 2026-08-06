package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.modules.admin.AdminConfig;
import de.kreiscraft.modules.admin.CapturingMessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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

    @Test
    void build_requiresPermission_whenMissing_returnsFalse() {
        LiteralCommandNode<CommandSourceStack> node =
                LastOnlineCommand.build(messages, server, () -> new AdminConfig(null, null));
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_whenGranted_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node =
                LastOnlineCommand.build(messages, server, () -> new AdminConfig(null, null));
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.lastonline", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_sendsLastOnlineKey() throws Exception {
        LiteralCommandNode<CommandSourceStack> node =
                LastOnlineCommand.build(messages, server, () -> new AdminConfig(null, null));
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
    void execute_usesConfiguredTimezone() throws Exception {
        // UTC+9 is a fixed-offset zone (no DST) so the offset is deterministic.
        AdminConfig configTz = new AdminConfig(null, "UTC+9");
        LiteralCommandNode<CommandSourceStack> node =
                LastOnlineCommand.build(messages, server, () -> configTz);
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
