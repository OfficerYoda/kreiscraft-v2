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
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class UnjailCommandTest {

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
        return UnjailCommand.build(service, messages);
    }

    @Test
    void build_commandNameIsUnjail() {
        assertThat(buildNode().getName()).isEqualTo("unjail");
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
        verify(service, never()).release(org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_targetNotJailed_sendsNotJailedMessage() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        when(service.release(target.getUniqueId())).thenReturn(Optional.empty());

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        verify(messages).send(admin, "jail.not-jailed", "player", "Target");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_releasesAndSendsReleasedMessage() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        Location restoreLocation = new Location(world, 10, 64, 10);
        JailEntry entry = new JailEntry(restoreLocation, "griefing", Instant.now());
        when(service.release(target.getUniqueId())).thenReturn(Optional.of(entry));

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(admin);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", String.class)).thenReturn("Target");

        node.getChild("target").getCommand().run(ctx);

        verify(messages).send(admin, "jail.released", "player", "Target");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_teleportsPlayerToRestoreLocation() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = buildNode();
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        Location restoreLocation = new Location(world, 10, 64, 10);
        JailEntry entry = new JailEntry(restoreLocation, "", Instant.now());
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

    @Test
    void build_hasTargetChildArgument() {
        assertThat(buildNode().getChild("target")).isNotNull();
    }
}
