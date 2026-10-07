package de.kreiscraft.modules.jail.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.jail.JailEntry;
import de.kreiscraft.modules.jail.JailService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Instant;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
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
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        service = mock(JailService.class);
        messages = mock(MessageService.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private LiteralCommandNode<CommandSourceStack> buildNode() {
        return JailCommand.build(service, messages);
    }

    // ── build ────────────────────────────────────────────────────────────────

    @Test
    void build_commandNameIsJail() {
        assertThat(buildNode().getName()).isEqualTo("jail");
    }

    @Test
    void build_hasTargetChildArgument() {
        assertThat(buildNode().getChildren()).isNotEmpty();
        assertThat(buildNode().getChild("target")).isNotNull();
    }

    // ── permission guard ─────────────────────────────────────────────────────

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

    // ── jail direction ───────────────────────────────────────────────────────

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
        when(service.isJailed(target.getUniqueId())).thenReturn(false);

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
    void execute_jailedTargetWithBypass_releasesRegardlessly() throws Exception {
        // bypass must not prevent releasing an already-jailed player
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        target.addAttachment(plugin, "kreiscraft.jail.bypass", true);
        JailEntry entry = new JailEntry(null, "", Instant.now());
        when(service.isJailed(target.getUniqueId())).thenReturn(true);
        when(service.release(target.getUniqueId())).thenReturn(Optional.of(entry));

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        verify(service).release(target.getUniqueId());
        verify(messages).send(admin, "jail.released", "player", "Target");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_unjailedTarget_jailsAndSendsJailedMessage() throws Exception {
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
        verify(messages).send(target, "jail.jailed-target", "reason", "");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_unjailedTarget_teleportsTargetToJailLocation() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        when(service.isJailed(target.getUniqueId())).thenReturn(false);
        Location jailLocation = new Location(world, 126, 91, 92);
        when(service.jailLocation()).thenReturn(Optional.of(jailLocation));

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        assertThat(target.getLocation().getBlockX()).isEqualTo(126);
        assertThat(target.getLocation().getBlockY()).isEqualTo(91);
        assertThat(target.getLocation().getBlockZ()).isEqualTo(92);
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_unjailedTarget_sendsReasonToTargetDirectly() throws Exception {
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

        // The target, not the sender, receives the localized reason-bearing message.
        verify(messages).send(target, "jail.jailed-target", "reason", "griefing");
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
        verify(messages).send(target, "jail.jailed-target", "reason", "griefing");
    }

    // ── release direction ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void execute_jailedTarget_releasesAndSendsReleasedMessage() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        Location restoreLocation = new Location(world, 10, 64, 10);
        JailEntry entry = new JailEntry(restoreLocation, "griefing", Instant.now());
        when(service.isJailed(target.getUniqueId())).thenReturn(true);
        when(service.release(target.getUniqueId())).thenReturn(Optional.of(entry));

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        verify(service).release(target.getUniqueId());
        verify(messages).send(admin, "jail.released", "player", "Target");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_jailedTarget_teleportsToRestoreLocation() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        Location restoreLocation = new Location(world, 10, 64, 10);
        JailEntry entry = new JailEntry(restoreLocation, "", Instant.now());
        when(service.isJailed(target.getUniqueId())).thenReturn(true);
        when(service.release(target.getUniqueId())).thenReturn(Optional.of(entry));

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        assertThat(target.getLocation().getBlockX()).isEqualTo(10);
        assertThat(target.getLocation().getBlockY()).isEqualTo(64);
        assertThat(target.getLocation().getBlockZ()).isEqualTo(10);
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_jailedTarget_doesNotCallJail() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        JailEntry entry = new JailEntry(null, "", Instant.now());
        when(service.isJailed(target.getUniqueId())).thenReturn(true);
        when(service.release(target.getUniqueId())).thenReturn(Optional.of(entry));

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        verify(service, never()).jail(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
