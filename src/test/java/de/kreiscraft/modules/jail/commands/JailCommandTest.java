package de.kreiscraft.modules.jail.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.jail.JailConfig;
import de.kreiscraft.modules.jail.JailService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class JailCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private JailService service;
    private MessageService messages;
    private AtomicReference<JailConfig> configRef;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        service = mock(JailService.class);
        messages = mock(MessageService.class);
        configRef = new AtomicReference<>(
                new JailConfig(new Location(world, 126, 91, 92), List.of("msg")));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private LiteralCommandNode<CommandSourceStack> buildNode() {
        return JailCommand.build(service, messages, configRef);
    }

    @Test
    void build_commandNameIsJail() {
        assertThat(buildNode().getName()).isEqualTo("jail");
    }

    @Test
    void requires_withoutPermission_returnsFalse() {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void requires_withPermission_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.jail.use", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_targetNotOnline_sendsNotFoundMessage() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock sender = server.addPlayer("Admin");

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(sender);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("NoSuchPlayer");

        node.getChild("target").getCommand().run(ctx);

        verify(messages).send(sender, "jail.target-not-found", "player", "NoSuchPlayer");
        verify(service, never()).jail(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_targetHasBypass_sendsBypassMessage() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        target.addAttachment(plugin, "kreiscraft.jail.bypass", true);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        verify(messages).send(admin, "jail.target-has-bypass", "player", "Target");
        verify(service, never()).jail(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_targetAlreadyJailed_sendsAlreadyJailedMessage() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        when(service.isJailed(target.getUniqueId())).thenReturn(true);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        verify(messages).send(admin, "jail.already-jailed", "player", "Target");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_jailsTargetAndSendsMessage() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        when(service.isJailed(target.getUniqueId())).thenReturn(false);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        verify(service).jail(org.mockito.ArgumentMatchers.eq(target.getUniqueId()),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(""));
        verify(messages).send(admin, "jail.jailed", "player", "Target", "reason", "");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_withReason_passesReasonToService() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        when(service.isJailed(target.getUniqueId())).thenReturn(false);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");
        when(ctx.getArgument("reason", String.class)).thenReturn("griefing");

        node.getChild("target").getChild("reason").getCommand().run(ctx);

        verify(service).jail(org.mockito.ArgumentMatchers.eq(target.getUniqueId()),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("griefing"));
        verify(messages).send(admin, "jail.jailed", "player", "Target", "reason", "griefing");
    }

    @Test
    void build_hasTargetChildArgument() {
        assertThat(buildNode().getChildren()).isNotEmpty();
        assertThat(buildNode().getChild("target")).isNotNull();
    }
}
