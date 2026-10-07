package de.kreiscraft.core.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Shared Brigadier completion providers used by Kreiscraft command trees.
 *
 * <p>Commands should consume these instead of re-implementing profile or registry scans, so that
 * every command offers the same candidate names and the same authorization. Each factory returns a
 * {@link SuggestionProvider} that:
 *
 * <ul>
 *   <li>emits nothing when the sender lacks the supplied permission — mirroring the command's own
 *       {@code .requires(...)} authorization so suggestions never leak to unauthorized senders;
 *   <li>restricts offline candidates to <em>known</em> profiles ({@link
 *       OfflinePlayer#hasPlayedBefore()}), matching the visibility rule used when a command opens a
 *       historical player's data;
 *   <li>filters candidates against the partially typed input (case-insensitively) and returns them
 *       in a deterministic order.
 * </ul>
 */
public final class SuggestionProviders {

    private SuggestionProviders() {}

    /** Suggests the names of every currently-online player. */
    public static SuggestionProvider<CommandSourceStack> onlinePlayers(
            Server server, String permission) {
        return gated(
                permission,
                (ctx, builder) -> {
                    server.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(Objects::nonNull)
                            .distinct()
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .forEach(name -> suggestMatching(builder, name));
                    return builder.buildFuture();
                });
    }

    /**
     * Suggests every currently-online player plus every known-offline profile (a profile that has
     * played before and still has a resolved name).
     */
    public static SuggestionProvider<CommandSourceStack> onlineAndKnownOfflineProfiles(
            Server server, String permission) {
        return gated(
                permission,
                (ctx, builder) -> {
                    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                    server.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(Objects::nonNull)
                            .forEach(names::add);
                    for (OfflinePlayer offline : server.getOfflinePlayers()) {
                        if (offline != null && offline.hasPlayedBefore() && offline.getName() != null) {
                            names.add(offline.getName());
                        }
                    }
                    names.forEach(name -> suggestMatching(builder, name));
                    return builder.buildFuture();
                });
    }

    /** Suggests every spawnable {@link EntityType} by its lowercase name. */
    public static SuggestionProvider<CommandSourceStack> entityTypes(String permission) {
        return gated(
                permission,
                (ctx, builder) -> {
                    Arrays.stream(EntityType.values())
                            .filter(EntityType::isSpawnable)
                            .map(type -> type.name().toLowerCase(Locale.ROOT))
                            .sorted()
                            .forEach(name -> suggestMatching(builder, name));
                    return builder.buildFuture();
                });
    }

    /** Suggests every {@link Particle} by its lowercase name. */
    public static SuggestionProvider<CommandSourceStack> particles(String permission) {
        return gated(
                permission,
                (ctx, builder) -> {
                    Arrays.stream(Particle.values())
                            .map(particle -> particle.name().toLowerCase(Locale.ROOT))
                            .sorted()
                            .forEach(name -> suggestMatching(builder, name));
                    return builder.buildFuture();
                });
    }

    private static SuggestionProvider<CommandSourceStack> gated(
            String permission, SuggestionProvider<CommandSourceStack> delegate) {
        return (ctx, builder) -> {
            if (!ctx.getSource().getSender().hasPermission(permission)) {
                return builder.buildFuture();
            }
            return delegate.getSuggestions(ctx, builder);
        };
    }

    private static void suggestMatching(SuggestionsBuilder builder, String value) {
        if (value.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
            builder.suggest(value);
        }
    }
}
