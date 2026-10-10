package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;

public final class LastOnlineCommand {

    /** Absolute timestamp rendered in the canonical {@link PlaceholderResolver#zone()}. */
    private static final DateTimeFormatter ABSOLUTE_FMT =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);

    private LastOnlineCommand() {}

    /**
     * Builds {@code /lastonline}. The absolute timestamp uses the canonical server timezone exposed
     * by {@link PlaceholderResolver#zone()} (Europe/Berlin) and is formatted in the recipient's
     * language; the relative duration is rendered from the {@code time.relative.*} language keys so
     * it reads naturally in German and English.
     */
    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Server server, PlaceholderResolver placeholders) {
        return Commands.literal("lastonline")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.lastonline"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(
                                SuggestionProviders.onlineAndKnownOfflineProfiles(
                                        server, "kreiscraft.admin.lastonline"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String targetName = StringArgumentType.getString(ctx, "player");
                            @SuppressWarnings("deprecation")
                            OfflinePlayer offlinePlayer = server.getOfflinePlayer(targetName);
                            long lastSeen = offlinePlayer.getLastSeen();
                            if (lastSeen <= 0) {
                                messages.send(
                                        sender,
                                        "admin.lastonline-never",
                                        "player",
                                        targetName);
                                return Command.SINGLE_SUCCESS;
                            }
                            ZoneId zone = placeholders.zone();
                            Instant lastSeenInstant = Instant.ofEpochMilli(lastSeen);
                            String when =
                                    ABSOLUTE_FMT
                                            .withLocale(messages.localeFor(sender))
                                            .withZone(zone)
                                            .format(lastSeenInstant);
                            Relative relative =
                                    relativeSince(lastSeenInstant, placeholders.clock().instant());
                            Component relativeText = renderRelative(messages, sender, relative);
                            messages.send(
                                    sender,
                                    "admin.lastonline",
                                    "player",
                                    targetName,
                                    "when",
                                    when,
                                    "relative",
                                    relativeText);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    /**
     * Renders the relative duration in the recipient's language. Each bucket has its own literal
     * key so the wording can differ per language rather than being assembled from fragments.
     */
    private static Component renderRelative(
            MessageService messages, CommandSender sender, Relative relative) {
        return switch (relative.bucket()) {
            case "just-now" -> messages.renderFor(sender, "time.relative.just-now");
            case "seconds" -> messages.renderFor(sender, "time.relative.seconds", "count", relative.count());
            case "minutes" -> messages.renderFor(sender, "time.relative.minutes", "count", relative.count());
            case "hours" -> messages.renderFor(sender, "time.relative.hours", "count", relative.count());
            case "days" -> messages.renderFor(sender, "time.relative.days", "count", relative.count());
            default -> Component.empty();
        };
    }

    /** A relative-duration language key plus the count it renders with. */
    record Relative(String bucket, long count) {
    }

    /**
     * Maps the elapsed time between {@code then} and {@code now} onto one of the relative-duration
     * buckets backed by language keys. Future timestamps clamp to "just now".
     */
    static Relative relativeSince(Instant then, Instant now) {
        long seconds = Math.max(0L, Duration.between(then, now).getSeconds());
        if (seconds < 10L) {
            return new Relative("just-now", 0L);
        }
        if (seconds < 60L) {
            return new Relative("seconds", seconds);
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return new Relative("minutes", minutes);
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return new Relative("hours", hours);
        }
        return new Relative("days", Duration.ofSeconds(seconds).toDays());
    }
}
