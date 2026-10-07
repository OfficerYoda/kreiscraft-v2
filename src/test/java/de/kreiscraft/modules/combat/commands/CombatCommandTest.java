package de.kreiscraft.modules.combat.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionTestSupport;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.combat.CombatService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.time.Duration;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CombatCommandTest {

    @Mock
    private CombatService service;
    @Mock
    private MessageService messages;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_returnsNonNullNode() {
        assertThat(CombatCommand.build(service, messages, server)).isNotNull();
    }

    @Test
    void build_nodeNameIsCombat() {
        assertThat(CombatCommand.build(service, messages, server).getName()).isEqualTo("combat");
    }

    @Test
    void permissionGate_withoutPermission_requiresFails() {
        LiteralCommandNode<CommandSourceStack> node = CombatCommand.build(service, messages, server);

        CommandSender noPerms = mock(CommandSender.class);
        when(noPerms.hasPermission("kreiscraft.combat.admin")).thenReturn(false);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(noPerms);

        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void permissionGate_withPermission_requiresPasses() {
        LiteralCommandNode<CommandSourceStack> node = CombatCommand.build(service, messages, server);

        CommandSender withPerms = mock(CommandSender.class);
        when(withPerms.hasPermission("kreiscraft.combat.admin")).thenReturn(true);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(withPerms);

        assertThat(node.getRequirement().test(src)).isTrue();
    }

    // -----------------------------------------------------------------------
    // Selector cardinality
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void execute_singleTarget_tagsOnlyThatPlayer() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = CombatCommand.build(service, messages, server);
        PlayerMock alice = server.addPlayer("Alice");

        CommandSourceStack src = mock(CommandSourceStack.class);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        PlayerSelectorArgumentResolver resolver = mock(PlayerSelectorArgumentResolver.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", PlayerSelectorArgumentResolver.class)).thenReturn(resolver);
        when(resolver.resolve(src)).thenReturn(List.of(alice));
        when(ctx.getArgument("seconds", int.class)).thenReturn(7);

        node.getChild("target").getChild("seconds").getCommand().run(ctx);

        verify(service).tag(alice.getUniqueId(), Duration.ofSeconds(7));
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_multiPlayerSelector_tagsEveryResolvedPlayerWithTheSameDuration() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = CombatCommand.build(service, messages, server);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        PlayerMock carol = server.addPlayer("Carol");

        CommandSourceStack src = mock(CommandSourceStack.class);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        PlayerSelectorArgumentResolver resolver = mock(PlayerSelectorArgumentResolver.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", PlayerSelectorArgumentResolver.class)).thenReturn(resolver);
        when(resolver.resolve(src)).thenReturn(List.of(alice, bob, carol));
        when(ctx.getArgument("seconds", int.class)).thenReturn(5);

        node.getChild("target").getChild("seconds").getCommand().run(ctx);

        verify(service).tag(alice.getUniqueId(), Duration.ofSeconds(5));
        verify(service).tag(bob.getUniqueId(), Duration.ofSeconds(5));
        verify(service).tag(carol.getUniqueId(), Duration.ofSeconds(5));
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_multiPlayerSelector_sendsOneManualTagPerPlayer() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = CombatCommand.build(service, messages, server);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        CommandSender sender = mock(CommandSender.class);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(sender);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        PlayerSelectorArgumentResolver resolver = mock(PlayerSelectorArgumentResolver.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("target", PlayerSelectorArgumentResolver.class)).thenReturn(resolver);
        when(resolver.resolve(src)).thenReturn(List.of(alice, bob));
        when(ctx.getArgument("seconds", int.class)).thenReturn(5);

        node.getChild("target").getChild("seconds").getCommand().run(ctx);

        verify(messages, times(2))
                .send(eq(sender), eq("combat.manual-tag"), any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Suggestions
    // -----------------------------------------------------------------------

    @Test
    void targetArgument_suggestsOnlinePlayerNames() {
        LiteralCommandNode<CommandSourceStack> node = CombatCommand.build(service, messages, server);
        server.addPlayer("Alice");
        server.addPlayer("Bob");

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("kreiscraft.combat.admin")).thenReturn(true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(sender);

        assertThat(SuggestionTestSupport.suggestions(node.getChild("target"), src, ""))
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void targetArgument_withoutPermission_suggestsNothing() {
        LiteralCommandNode<CommandSourceStack> node = CombatCommand.build(service, messages, server);
        server.addPlayer("Alice");

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("kreiscraft.combat.admin")).thenReturn(false);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(sender);

        assertThat(SuggestionTestSupport.suggestions(node.getChild("target"), src, "")).isEmpty();
    }
}
