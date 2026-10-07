package de.kreiscraft.core.command;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Test helper for asserting that a command argument is wired to a shared {@link
 * SuggestionProviders} provider. Invokes the registered {@code SuggestionProvider} directly through
 * the argument node, which is the seam that Paper uses for tab-completion.
 */
public final class SuggestionTestSupport {

    private SuggestionTestSupport() {}

    @SuppressWarnings("unchecked")
    public static List<String> suggestions(
            CommandNode<CommandSourceStack> argumentNode, CommandSourceStack source, String input) {
        ArgumentCommandNode<CommandSourceStack, ?> node =
                (ArgumentCommandNode<CommandSourceStack, ?>) argumentNode;
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        when(context.getSource()).thenReturn(source);
        SuggestionsBuilder builder = new SuggestionsBuilder(input, 0);
        try {
            return node.getCustomSuggestions()
                    .getSuggestions(context, builder)
                    .thenApply(
                            suggestions -> suggestions.getList().stream()
                                    .map(Suggestion::getText)
                                    .toList())
                    .get();
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
