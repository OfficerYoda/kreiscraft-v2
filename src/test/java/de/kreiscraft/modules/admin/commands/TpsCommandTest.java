package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.modules.admin.CapturingMessageService;
import de.kreiscraft.modules.admin.TpsBar;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
class TpsCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private CapturingMessageService messages;

    @Mock
    private PlaceholderResolver resolver;

    @Mock
    private TpsBar bar;

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
        LiteralCommandNode<CommandSourceStack> node = TpsCommand.build(messages, resolver, bar);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_whenGranted_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node = TpsCommand.build(messages, resolver, bar);
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.tps", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_sendsTpsKey() throws Exception {
        when(resolver.resolveString(any(), any())).thenReturn("20.0");
        LiteralCommandNode<CommandSourceStack> node = TpsCommand.build(messages, resolver, bar);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);

        node.getCommand().run(ctx);

        assertThat(messages.sentKeys).containsExactly("admin.tps");
    }

    @Test
    void bar_togglesAndReportsState() throws Exception {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.tps", true);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        when(bar.toggle(player)).thenReturn(true, false);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        assertThat(dispatcher.execute("tps bar", source)).isEqualTo(1);
        assertThat(dispatcher.execute("tps bar", source)).isEqualTo(1);

        assertThat(messages.sentKeys).containsExactly("admin.tps-bar.enabled", "admin.tps-bar.disabled");
    }

    @Test
    void bar_deniesPlayerWithoutPermission() {
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        assertThatThrownBy(() -> dispatcher.execute("tps bar", source)).isInstanceOf(CommandSyntaxException.class);
        verifyNoInteractions(bar);
    }

    @Test
    void console_canReadTpsButCannotToggleBar() throws Exception {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(server.getConsoleSender());
        when(resolver.resolveString("%tps%", null)).thenReturn("20.00");
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        assertThat(dispatcher.execute("tps", source)).isEqualTo(1);
        verify(resolver).resolveString("%tps%", null);
        assertThatThrownBy(() -> dispatcher.execute("tps bar", source)).isInstanceOf(CommandSyntaxException.class);
        verifyNoInteractions(bar);
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(TpsCommand.build(messages, resolver, bar));
        return dispatcher;
    }
}
