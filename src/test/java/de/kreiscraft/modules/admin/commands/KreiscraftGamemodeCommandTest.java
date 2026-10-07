package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.GameMode;
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
class KreiscraftGamemodeCommandTest {

    private ServerMock server;
    private PluginMock plugin;

    @Mock
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_requiresPermission_whenMissing_returnsFalse() {
        LiteralCommandNode<CommandSourceStack> node =
                KreiscraftGamemodeCommand.build(messages, server);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_whenGranted_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node =
                KreiscraftGamemodeCommand.build(messages, server);
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.gamemode", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_self_setsGameMode() throws Exception {
        LiteralCommandNode<CommandSourceStack> node =
                KreiscraftGamemodeCommand.build(messages, server);
        PlayerMock player = server.addPlayer("Alice");
        player.setGameMode(GameMode.SURVIVAL);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("mode", String.class)).thenReturn("creative");

        node.getChild("mode").getCommand().run(ctx);

        assertThat(player.getGameMode()).isEqualTo(GameMode.CREATIVE);
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_target_setsTargetGameMode() throws Exception {
        LiteralCommandNode<CommandSourceStack> node =
                KreiscraftGamemodeCommand.build(messages, server);
        PlayerMock executor = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        target.setGameMode(GameMode.SURVIVAL);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(executor);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("mode", String.class)).thenReturn("creative");
        when(ctx.getArgument("player", String.class)).thenReturn("Target");

        node.getChild("mode").getChild("player").getCommand().run(ctx);

        assertThat(target.getGameMode()).isEqualTo(GameMode.CREATIVE);
    }

    @Test
    void build_suggestsOnlinePlayersForTarget() {
        LiteralCommandNode<CommandSourceStack> node =
                KreiscraftGamemodeCommand.build(messages, server);
        PlayerMock executor = server.addPlayer("Admin");
        server.addPlayer("Target");
        executor.addAttachment(plugin, "kreiscraft.admin.gamemode", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(executor);

        assertThat(
                de.kreiscraft.core.command.SuggestionTestSupport.suggestions(
                        node.getChild("mode").getChild("player"), src, ""))
                .contains("Admin", "Target");
    }
}
