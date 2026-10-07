package de.kreiscraft.modules.effects.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionTestSupport;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.effects.EffectsService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.UUID;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class EffectsCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private EffectsService service;
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        service = mock(EffectsService.class);
        messages = mock(MessageService.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(EffectsCommand.build(service, messages));
        return dispatcher;
    }

    private CommandSourceStack sourceFor(Player player) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        return source;
    }

    private PlayerMock permittedPlayer() {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.effects.use", true);
        return player;
    }

    @Test
    void build_commandNameIsEffects() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(mock(EffectsService.class), mock(MessageService.class));
        assertThat(node.getName()).isEqualTo("effects");
    }

    @Test
    void build_requiresPermission_deniesWithoutPermission() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(mock(EffectsService.class), mock(MessageService.class));

        CommandSourceStack src = mock(CommandSourceStack.class);
        Player player = mock(Player.class);
        when(src.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.effects.use")).thenReturn(false);

        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_allowsWithPermission() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(mock(EffectsService.class), mock(MessageService.class));

        CommandSourceStack src = mock(CommandSourceStack.class);
        Player player = mock(Player.class);
        when(src.getSender()).thenReturn(player);
        when(player.hasPermission("kreiscraft.effects.use")).thenReturn(true);

        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @Test
    void build_exposesToggleAndParticleChildren() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(mock(EffectsService.class), mock(MessageService.class));

        assertThat(node.getChild("toggle")).isNotNull();
        assertThat(node.getChild("particle")).isNotNull();
    }

    // ------------------------------------------------------------------
    // Bare invocation: report only, no mutation
    // ------------------------------------------------------------------

    @Test
    void bare_reportsStateParticleAndUsage_withoutMutating() throws CommandSyntaxException {
        PlayerMock player = permittedPlayer();
        UUID id = player.getUniqueId();
        when(service.isEnabled(id)).thenReturn(true);
        when(service.getParticle(id)).thenReturn(Particle.HAPPY_VILLAGER);

        dispatcher().execute("effects", sourceFor(player));

        verify(service, never()).toggle(any(UUID.class));
        verify(service, never()).setParticle(any(UUID.class), any(Particle.class));
        verify(messages).send(player, "effects.status", "state", null, "particle", "HAPPY_VILLAGER");
        verify(messages).send(player, "effects.usage");
    }

    // ------------------------------------------------------------------
    // Explicit toggle
    // ------------------------------------------------------------------

    @Test
    void toggle_flipsAndSendsEnabledMessage() throws CommandSyntaxException {
        PlayerMock player = permittedPlayer();
        UUID id = player.getUniqueId();
        when(service.isEnabled(id)).thenReturn(false);

        dispatcher().execute("effects toggle", sourceFor(player));

        verify(service).toggle(id);
        verify(messages).send(player, "effects.disabled");
    }

    // ------------------------------------------------------------------
    // Particle selection
    // ------------------------------------------------------------------

    @Test
    void particle_setsAndPersistsParticle() throws CommandSyntaxException {
        PlayerMock player = permittedPlayer();
        UUID id = player.getUniqueId();

        dispatcher().execute("effects flame", sourceFor(player));

        verify(service).setParticle(id, Particle.FLAME);
        verify(messages).send(player, "effects.particle-set", "particle", "FLAME");
    }

    @Test
    void particle_unknown_sendsErrorAndDoesNotMutate() throws CommandSyntaxException {
        PlayerMock player = permittedPlayer();

        dispatcher().execute("effects not_a_particle", sourceFor(player));

        verify(service, never()).setParticle(any(UUID.class), any(Particle.class));
        verify(messages).send(player, "effects.unknown-particle", "particle", "NOT_A_PARTICLE");
    }

    // ------------------------------------------------------------------
    // Completion reflects the effective preference
    // ------------------------------------------------------------------

    @Test
    void particleArgument_suggestsParticleNames() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(service, messages);
        CommandSourceStack src = mock(CommandSourceStack.class);
        Player player = mock(Player.class);
        when(src.getSender()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("kreiscraft.effects.use")).thenReturn(true);
        when(service.getParticle(any(UUID.class))).thenReturn(Particle.FLAME);

        List<String> suggestions =
                SuggestionTestSupport.suggestions(node.getChild("particle"), src, "flam");

        assertThat(suggestions).contains("flame");
    }

    @Test
    void particleArgument_readsEffectivePreferenceAtCompletionTime() {
        LiteralCommandNode<CommandSourceStack> node =
                EffectsCommand.build(service, messages);
        CommandSourceStack src = mock(CommandSourceStack.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(src.getSender()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission("kreiscraft.effects.use")).thenReturn(true);
        when(service.getParticle(id)).thenReturn(Particle.FLAME);

        SuggestionTestSupport.suggestions(node.getChild("particle"), src, "");

        verify(service).getParticle(id);
    }
}
