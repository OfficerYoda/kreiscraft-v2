package de.kreiscraft.core.message;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public final class PlaceholderResolver {

    private static final Pattern TOKEN = Pattern.compile("%([a-zA-Z_]+)%");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Server server;
    private final ZoneId zone;
    private final Clock clock;

    public PlaceholderResolver(Server server, ZoneId zone, Clock clock) {
        this.server = server;
        this.zone = zone;
        this.clock = clock;
    }

    /**
     * The canonical server timezone ({@code core.timezone}). Absolute times shown to players and
     * operators are derived from this single zone.
     */
    public ZoneId zone() {
        return zone;
    }

    /**
     * The clock used to evaluate time-dependent placeholders. Exposed so commands that render
     * relative durations can compute "now" from the same clock the resolver uses, keeping tests
     * deterministic.
     */
    public Clock clock() {
        return clock;
    }

    public String resolveString(String template, Player context) {
        Matcher m = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = resolveOne(m.group(1), context);
            m.appendReplacement(out, value == null
                    ? Matcher.quoteReplacement(m.group())
                    : Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String resolveOne(String key, Player context) {
        return switch (key) {
            case "player_name" -> context == null ? null : context.getName();
            case "server_players" -> String.valueOf(server.getOnlinePlayers().size());
            case "tps" -> formatTps(server.getTPS()[0]);
            case "system_time" -> LocalTime.now(clock.withZone(zone)).format(TIME_FMT);
            case "ping" -> context == null ? null : String.valueOf(context.getPing());
            case "playtime" -> context == null ? null
                    : formatPlaytime(context.getStatistic(Statistic.PLAY_ONE_MINUTE));
            case "plugin_prefix" -> "[Kreiscraft]";
            default -> null;
        };
    }

    /**
     * Formats TPS the way the original tab list did: capped at 20.0 and rounded to at most two
     * decimal places (e.g. {@code "20.0"}, {@code "19.87"}).
     */
    private static String formatTps(double tps) {
        return String.valueOf(Math.min(20.0, Math.round(tps * 100.0) / 100.0));
    }

    /**
     * Formats a {@code PLAY_ONE_MINUTE} tick count as {@code HH:MM:SS}, matching the original tab
     * list display precision.
     */
    private static String formatPlaytime(int ticks) {
        long seconds = ticks / 20L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}
