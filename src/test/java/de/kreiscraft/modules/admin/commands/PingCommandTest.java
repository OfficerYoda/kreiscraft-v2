package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionTestSupport;
import de.kreiscraft.modules.admin.CapturingMessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class PingCommandTest {

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
        LiteralCommandNode<CommandSourceStack> node = PingCommand.build(messages, server);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_whenGranted_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node = PingCommand.build(messages, server);
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.ping", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_selfPing_sendsPingKey() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = PingCommand.build(messages, server);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);

        node.getCommand().run(ctx);

        assertThat(messages.sentKeys).containsExactly("admin.ping");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_targetNotFound_sendsNotFoundKey() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = PingCommand.build(messages, server);
        PlayerMock executor = server.addPlayer("Admin");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(executor);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("player", String.class)).thenReturn("NonExistent");

        node.getChild("player").getCommand().run(ctx);

        assertThat(messages.sentKeys).containsExactly("admin.target-not-found");
    }

    @Test
    void playerArgument_suggestsOnlinePlayers() {
        LiteralCommandNode<CommandSourceStack> node = PingCommand.build(messages, server);
        PlayerMock admin = server.addPlayer("Admin");
        server.addPlayer("Alice");
        admin.addAttachment(plugin, "kreiscraft.admin.ping", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);

        List<String> suggestions =
                SuggestionTestSupport.suggestions(node.getChild("player"), src, "");

        assertThat(suggestions).containsExactly("Admin", "Alice");
    }
}
