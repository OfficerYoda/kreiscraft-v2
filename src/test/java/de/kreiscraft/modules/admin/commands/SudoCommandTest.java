package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.modules.admin.CapturingMessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class SudoCommandTest {

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
        LiteralCommandNode<CommandSourceStack> node = SudoCommand.build(messages, server);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_whenGranted_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node = SudoCommand.build(messages, server);
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.sudo", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_validTarget_sendsSudoExecutedKey() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = SudoCommand.build(messages, server);
        PlayerMock executor = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(executor);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");
        when(ctx.getArgument("text", String.class)).thenReturn("hello world");

        node.getChild("target").getChild("text").getCommand().run(ctx);

        assertThat(messages.sentKeys).containsExactly("admin.sudo-executed");
    }

    @Test
    void build_suggestsAllAndOnlinePlayersForTarget() {
        LiteralCommandNode<CommandSourceStack> node = SudoCommand.build(messages, server);
        PlayerMock executor = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        executor.addAttachment(plugin, "kreiscraft.admin.sudo", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(executor);

        assertThat(
                de.kreiscraft.core.command.SuggestionTestSupport.suggestions(
                        node.getChild("target"), src, ""))
                .contains("all", "Admin", "Target");
    }
}
