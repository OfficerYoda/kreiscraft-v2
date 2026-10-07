package de.kreiscraft.modules.glow.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionTestSupport;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.glow.GlowService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class GlowCommandTest {

    @Mock
    private GlowService service;
    @Mock
    private MessageService messages;
    @Mock
    private Player player;
    @Mock
    private CommandSourceStack source;
    @Mock
    private CommandContext<CommandSourceStack> ctx;

    private ServerMock server;
    private LiteralCommandNode<CommandSourceStack> node;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        node = GlowCommand.build(service, messages, server);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -----------------------------------------------------------------------
    // Self toggle
    // -----------------------------------------------------------------------

    @Test
    void execute_selfToggle_on_sendsEnabledMessage() throws Exception {
        when(source.getSender()).thenReturn(player);
        when(ctx.getSource()).thenReturn(source);
        when(service.toggle(player)).thenReturn(true);

        node.getCommand().run(ctx);

        verify(service).toggle(player);
        verify(messages).send(player, "glow.enabled");
    }

    @Test
    void execute_selfToggle_off_sendsDisabledMessage() throws Exception {
        when(source.getSender()).thenReturn(player);
        when(ctx.getSource()).thenReturn(source);
        when(service.toggle(player)).thenReturn(false);

        node.getCommand().run(ctx);

        verify(service).toggle(player);
        verify(messages).send(player, "glow.disabled");
    }

    @Test
    void execute_whenSelfSenderIsNotPlayer_returnsSuccess() throws Exception {
        CommandSender consoleSender = mock(CommandSender.class);
        when(source.getSender()).thenReturn(consoleSender);
        when(ctx.getSource()).thenReturn(source);

        int result = node.getCommand().run(ctx);

        assertThat(result).isEqualTo(Command.SINGLE_SUCCESS);
    }

    @Test
    void selfRequires_withUsePermission_returnsTrue() {
        when(source.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.glow.use")).thenReturn(true);

        assertThat(node.getRequirement().test(source)).isTrue();
    }

    @Test
    void selfRequires_withoutUsePermission_returnsFalse() {
        when(source.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.glow.use")).thenReturn(false);

        assertThat(node.getRequirement().test(source)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Other-player toggle
    // -----------------------------------------------------------------------

    @Test
    void targetRequires_withOtherPermission_returnsTrue() {
        when(source.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.glow.other")).thenReturn(true);

        assertThat(node.getChild("target").getRequirement().test(source)).isTrue();
    }

    @Test
    void targetRequires_withoutOtherPermission_returnsFalse() {
        when(source.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.glow.other")).thenReturn(false);

        assertThat(node.getChild("target").getRequirement().test(source)).isFalse();
    }

    @Test
    void execute_otherToggle_on_notifiesActorAndTarget() throws Exception {
        Player actor = mock(Player.class);
        Player target = mock(Player.class);
        when(actor.getName()).thenReturn("Alice");
        when(target.getName()).thenReturn("Bob");
        when(source.getSender()).thenReturn(actor);
        when(ctx.getSource()).thenReturn(source);
        PlayerSelectorArgumentResolver resolver = mock(PlayerSelectorArgumentResolver.class);
        when(ctx.getArgument("target", PlayerSelectorArgumentResolver.class)).thenReturn(resolver);
        when(resolver.resolve(source)).thenReturn(List.of(target));
        when(service.toggle(target)).thenReturn(true);

        node.getChild("target").getCommand().run(ctx);

        verify(service).toggle(target);
        verify(messages).send(actor, "glow.other-enabled", "player", "Bob");
        verify(messages).send(target, "glow.target-enabled", "player", "Alice");
    }

    @Test
    void execute_otherToggle_off_notifiesActorAndTarget() throws Exception {
        Player actor = mock(Player.class);
        Player target = mock(Player.class);
        when(actor.getName()).thenReturn("Alice");
        when(target.getName()).thenReturn("Bob");
        when(source.getSender()).thenReturn(actor);
        when(ctx.getSource()).thenReturn(source);
        PlayerSelectorArgumentResolver resolver = mock(PlayerSelectorArgumentResolver.class);
        when(ctx.getArgument("target", PlayerSelectorArgumentResolver.class)).thenReturn(resolver);
        when(resolver.resolve(source)).thenReturn(List.of(target));
        when(service.toggle(target)).thenReturn(false);

        node.getChild("target").getCommand().run(ctx);

        verify(service).toggle(target);
        verify(messages).send(actor, "glow.other-disabled", "player", "Bob");
        verify(messages).send(target, "glow.target-disabled", "player", "Alice");
    }

    // -----------------------------------------------------------------------
    // Suggestions
    // -----------------------------------------------------------------------

    @Test
    void targetArgument_suggestsOnlinePlayerNames() {
        server.addPlayer("Alice");
        server.addPlayer("Bob");

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("kreiscraft.glow.other")).thenReturn(true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(sender);

        assertThat(SuggestionTestSupport.suggestions(node.getChild("target"), src, ""))
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void targetArgument_withoutPermission_suggestsNothing() {
        server.addPlayer("Alice");

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("kreiscraft.glow.other")).thenReturn(false);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(sender);

        assertThat(SuggestionTestSupport.suggestions(node.getChild("target"), src, "")).isEmpty();
    }
}
