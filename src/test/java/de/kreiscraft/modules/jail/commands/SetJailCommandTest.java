package de.kreiscraft.modules.jail.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.jail.JailService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class SetJailCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private JailService service;
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        service = mock(JailService.class);
        messages = mock(MessageService.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private LiteralCommandNode<CommandSourceStack> buildNode() {
        return SetJailCommand.build(service, messages);
    }

    @Test
    void build_commandNameIsSetjail() {
        assertThat(buildNode().getName()).isEqualTo("setjail");
    }

    @Test
    void requires_withoutPermission_returnsFalse() {
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(buildNode().getRequirement().test(src)).isFalse();
    }

    @Test
    void requires_withPermission_returnsTrue() {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.jail.set", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(buildNode().getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_asPlayer_storesCurrentLocationAndConfirms() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock player = server.addPlayer("Alice");
        Location current = player.getLocation();

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);

        node.getCommand().run(ctx);

        verify(service).setJailLocation(current);
        verify(messages).send(player, "jail.set");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_fromConsole_sendsPlayerOnlyAndWritesNothing() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        ConsoleCommandSender console = server.getConsoleSender();

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(console);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);

        node.getCommand().run(ctx);

        verify(service, never()).setJailLocation(org.mockito.ArgumentMatchers.any());
        verify(messages).send(console, "core.player-only");
    }

    @Test
    void build_smokeTest_doesNotThrow() {
        PlayerMock player = server.addPlayer("alice");
        assertThat(SetJailCommand.build(service, messages)).isNotNull();
        assertThat(player).isNotNull();
    }
}
