package de.kreiscraft.core.message;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class PlaceholderResolver {

    private static final Pattern TOKEN = Pattern.compile("%([a-zA-Z_]+)%");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final Server server;
    private final ZoneId zone;
    private final Clock clock;

    public PlaceholderResolver(Server server, ZoneId zone, Clock clock) {
        this.server = server;
        this.zone = zone;
        this.clock = clock;
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
            case "tps" -> String.format("%.1f", server.getTPS()[0]);
            case "system_time" -> LocalTime.now(clock.withZone(zone)).format(TIME_FMT);
            case "ping" -> context == null ? null : String.valueOf(context.getPing());
            case "playtime" -> context == null ? null
                    : String.valueOf(
                            context.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20 / 60);
            case "plugin_prefix" -> "[Kreiscraft]";
            default -> null;
        };
    }
}
