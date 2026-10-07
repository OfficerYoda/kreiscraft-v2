package de.kreiscraft.modules.jail.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

/** Tests the {@code /kerker} alias — identical behavior and permission to {@code /jail}. */
class KerkerCommandTest {

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
        return JailCommand.buildWithName("kerker", service, messages);
    }

    @Test
    void build_commandNameIsKerker() {
        assertThat(buildNode().getName()).isEqualTo("kerker");
    }

    @Test
    void requires_samePermissionAsJail_withoutPermission_returnsFalse() {
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(buildNode().getRequirement().test(src)).isFalse();
    }

    @Test
    void requires_samePermissionAsJail_withPermission_returnsTrue() {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.jail.use", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(buildNode().getRequirement().test(src)).isTrue();
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
    void execute_jailedTarget_releasesAndSendsReleasedMessage() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        JailEntry entry = new JailEntry(new Location(world, 5, 64, 5), "", Instant.now());
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

    @Test
    void build_hasTargetChildArgument() {
        assertThat(buildNode().getChild("target")).isNotNull();
    }
}
