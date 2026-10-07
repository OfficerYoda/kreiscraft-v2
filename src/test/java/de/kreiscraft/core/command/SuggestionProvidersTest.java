package de.kreiscraft.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the shared Brigadier completion providers.
 *
 * <p>Every provider is exercised through its public {@link SuggestionProvider} seam: the
 * suggestions it emits for a representative input, the ordering, the exclusions, and the
 * permission gate that matches the consuming command's own authorization.
 */
class SuggestionProvidersTest {

    private static final String PERMISSION = "kreiscraft.test.use";

    // ---------------------------------------------------------------------
    // onlinePlayers
    // ---------------------------------------------------------------------

    @Test
    void onlinePlayers_suggestsEveryOnlineNameInCaseInsensitiveOrder()
            throws ExecutionException, InterruptedException {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers())
                .thenAnswer(invocation -> List.of(player("bob"), player("Alice")));

        List<String> suggestions = suggestions(SuggestionProviders.onlinePlayers(server, PERMISSION), "", true);

        assertThat(suggestions).containsExactly("Alice", "bob");
    }

    @Test
    void onlinePlayers_filtersByRemainingInputCaseInsensitively()
            throws ExecutionException, InterruptedException {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers())
                .thenAnswer(invocation -> List.of(player("bob"), player("Alice")));

        List<String> suggestions =
                suggestions(SuggestionProviders.onlinePlayers(server, PERMISSION), "BO", true);

        assertThat(suggestions).containsExactly("bob");
    }

    @Test
    void onlinePlayers_withoutPermission_suggestsNothing()
            throws ExecutionException, InterruptedException {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenAnswer(invocation -> List.of(player("Alice")));

        List<String> suggestions =
                suggestions(SuggestionProviders.onlinePlayers(server, PERMISSION), "", false);

        assertThat(suggestions).isEmpty();
    }

    // ---------------------------------------------------------------------
    // onlineAndKnownOfflineProfiles
    // ---------------------------------------------------------------------

    @Test
    void onlineAndKnownOfflineProfiles_includesOnlineAndKnownOfflineDeduplicatedAndSorted()
            throws ExecutionException, InterruptedException {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenAnswer(invocation -> List.of(player("Alice")));
        OfflinePlayer dupOffline = offline("alice", true);
        OfflinePlayer knownCarol = offline("Carol", true);
        OfflinePlayer unknownDave = offline("Dave", false);
        OfflinePlayer unnamedEve = offline("Eve", true, null);
        when(server.getOfflinePlayers())
                .thenReturn(new OfflinePlayer[] {dupOffline, knownCarol, unknownDave, unnamedEve});

        List<String> suggestions =
                suggestions(
                        SuggestionProviders.onlineAndKnownOfflineProfiles(server, PERMISSION),
                        "",
                        true);

        assertThat(suggestions).containsExactly("Alice", "Carol");
    }

    @Test
    void onlineAndKnownOfflineProfiles_filtersByRemainingInput()
            throws ExecutionException, InterruptedException {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenAnswer(invocation -> List.of(player("Alice")));
        OfflinePlayer knownCarol = offline("Carol", true);
        when(server.getOfflinePlayers()).thenReturn(new OfflinePlayer[] {knownCarol});

        List<String> suggestions =
                suggestions(
                        SuggestionProviders.onlineAndKnownOfflineProfiles(server, PERMISSION),
                        "ca",
                        true);

        assertThat(suggestions).containsExactly("Carol");
    }

    @Test
    void onlineAndKnownOfflineProfiles_withoutPermission_suggestsNothing()
            throws ExecutionException, InterruptedException {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenAnswer(invocation -> List.of(player("Alice")));

        List<String> suggestions =
                suggestions(
                        SuggestionProviders.onlineAndKnownOfflineProfiles(server, PERMISSION),
                        "",
                        false);

        assertThat(suggestions).isEmpty();
    }

    // ---------------------------------------------------------------------
    // allAndOnlinePlayers
    // ---------------------------------------------------------------------

    @Test
    void allAndOnlinePlayers_suggestsAllFirstThenOnlineNames()
            throws ExecutionException, InterruptedException {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers())
                .thenAnswer(invocation -> List.of(player("bob"), player("Alice")));

        List<String> suggestions =
                suggestions(SuggestionProviders.allAndOnlinePlayers(server, PERMISSION), "", true);

        // Brigadier returns suggestions in case-insensitive sorted order.
        assertThat(suggestions).containsExactly("Alice", "all", "bob");
    }

    @Test
    void allAndOnlinePlayers_filtersByRemainingInput()
            throws ExecutionException, InterruptedException {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers())
                .thenAnswer(invocation -> List.of(player("bob"), player("Alice")));

        List<String> suggestions =
                suggestions(SuggestionProviders.allAndOnlinePlayers(server, PERMISSION), "al", true);

        assertThat(suggestions).containsExactly("Alice", "all");
    }

    @Test
    void allAndOnlinePlayers_withoutPermission_suggestsNothing()
            throws ExecutionException, InterruptedException {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenAnswer(invocation -> List.of(player("Alice")));

        List<String> suggestions =
                suggestions(SuggestionProviders.allAndOnlinePlayers(server, PERMISSION), "", false);

        assertThat(suggestions).isEmpty();
    }

    // ---------------------------------------------------------------------
    // entityTypes
    // ---------------------------------------------------------------------

    @Test
    void entityTypes_suggestsSpawnableLowercaseNamesInOrder()
            throws ExecutionException, InterruptedException {
        List<String> suggestions = suggestions(SuggestionProviders.entityTypes(PERMISSION), "", true);

        assertThat(suggestions).contains("zombie", "creeper", "pig");
        assertThat(suggestions).doesNotContain("player");
        assertThat(suggestions).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(suggestions).allMatch(name -> name.equals(name.toLowerCase()));
    }

    @Test
    void entityTypes_filtersByRemainingInput()
            throws ExecutionException, InterruptedException {
        List<String> suggestions =
                suggestions(SuggestionProviders.entityTypes(PERMISSION), "zomb", true);

        assertThat(suggestions).contains("zombie");
        assertThat(suggestions).allMatch(name -> name.startsWith("zomb"));
    }

    @Test
    void entityTypes_withoutPermission_suggestsNothing()
            throws ExecutionException, InterruptedException {
        List<String> suggestions =
                suggestions(SuggestionProviders.entityTypes(PERMISSION), "", false);

        assertThat(suggestions).isEmpty();
    }

    // ---------------------------------------------------------------------
    // particles
    // ---------------------------------------------------------------------

    @Test
    void particles_suggestsLowercaseNamesInOrder() throws ExecutionException, InterruptedException {
        List<String> suggestions = suggestions(SuggestionProviders.particles(PERMISSION), "", true);

        assertThat(suggestions).contains("flame", "heart");
        assertThat(suggestions).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(suggestions).allMatch(name -> name.equals(name.toLowerCase()));
    }

    @Test
    void particles_filtersByRemainingInput() throws ExecutionException, InterruptedException {
        List<String> suggestions =
                suggestions(SuggestionProviders.particles(PERMISSION), "he", true);

        assertThat(suggestions).containsExactly("heart");
    }

    @Test
    void particles_withoutPermission_suggestsNothing()
            throws ExecutionException, InterruptedException {
        List<String> suggestions = suggestions(SuggestionProviders.particles(PERMISSION), "", false);

        assertThat(suggestions).isEmpty();
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<String> suggestions(
            SuggestionProvider<CommandSourceStack> provider, String input, boolean permitted)
            throws ExecutionException, InterruptedException {
        CommandSourceStack source = source(permitted);
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        when(context.getSource()).thenReturn(source);
        SuggestionsBuilder builder = new SuggestionsBuilder(input, 0);
        try {
            return provider.getSuggestions(context, builder)
                    .thenApply(
                            suggestions -> suggestions.getList().stream().map(Suggestion::getText).toList())
                    .get();
        } catch (CommandSyntaxException e) {
            throw new ExecutionException(e);
        }
    }

    private static CommandSourceStack source(boolean permitted) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(PERMISSION)).thenReturn(permitted);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        return source;
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static OfflinePlayer offline(String name, boolean playedBefore) {
        return offline(name, playedBefore, name);
    }

    private static OfflinePlayer offline(String name, boolean playedBefore, String resolvedName) {
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.hasPlayedBefore()).thenReturn(playedBefore);
        when(offline.getName()).thenReturn(resolvedName);
        return offline;
    }

    // Guards against accidental reliance on the full registry contents: the provider must
    // expose at least the well-known representative values used by the consuming commands.
    @Test
    void entityTypeRegistryStillHasRepresentativeValues() {
        assertThat(Arrays.stream(EntityType.values()).anyMatch(t -> t.name().equals("ZOMBIE")))
                .isTrue();
        assertThat(Arrays.stream(Particle.values()).anyMatch(p -> p.name().equals("FLAME")))
                .isTrue();
    }
}
