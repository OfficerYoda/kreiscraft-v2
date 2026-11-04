package de.kreiscraft.modules.admin;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Per-player performance bars with persisted visibility. All access happens on the server thread. */
public final class TpsBar implements PriorityListener {

    private final MessageService messages;
    private final Server server;
    private final Supplier<TpsBarConfig> config;
    private final PlayerPreferencesService preferences;
    private final Map<UUID, View> views = new HashMap<>();

    public TpsBar(MessageService messages, Server server, Supplier<TpsBarConfig> config,
            PlayerPreferencesService preferences) {
        this.messages = messages;
        this.server = server;
        this.config = config;
        this.preferences = preferences;
    }

    /** Toggles the player's saved choice and returns whether the bar is now enabled. */
    public boolean toggle(Player player) {
        boolean enabled = !preferences.preferences(player.getUniqueId()).tpsBarEnabled().orElse(false);
        preferences.setTpsBarEnabled(player.getUniqueId(), enabled);
        View previous = views.remove(player.getUniqueId());
        if (previous != null) {
            previous.hide();
        }
        if (enabled) {
            restore(player);
        }
        return enabled;
    }

    /** Restores a saved choice without toggling or writing it; safe to call more than once. */
    void restore(Player player) {
        if (!player.isOnline() || !player.hasPermission("kreiscraft.admin.tps")
                || views.containsKey(player.getUniqueId())
                || !preferences.preferences(player.getUniqueId()).tpsBarEnabled().orElse(false)) {
            return;
        }
        Display display = display(player);
        BossBar bar = BossBar.bossBar(display.title(), display.progress(), display.color(), BossBar.Overlay.NOTCHED_20);
        views.put(player.getUniqueId(), new View(player, bar));
        player.showBossBar(bar);
    }

    public void tick() {
        var iterator = views.values().iterator();
        while (iterator.hasNext()) {
            View view = iterator.next();
            if (!view.player().isOnline() || !view.player().hasPermission("kreiscraft.admin.tps")) {
                view.hide();
                iterator.remove();
            } else {
                Display display = display(view.player());
                view.bar().name(display.title()).color(display.color()).progress(display.progress());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        restore(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        View view = views.remove(event.getPlayer().getUniqueId());
        if (view != null) {
            view.hide();
        }
    }

    public void shutdown() {
        views.values().forEach(View::hide);
        views.clear();
    }

    private Display display(Player player) {
        TpsBarConfig settings = config.get();
        double tps = Math.min(20.0, server.getTPS()[0]);
        double mspt = server.getAverageTickTime();
        int ping = player.getPing();
        Health tpsHealth = atLeast(tps, settings.tpsGreenMin(), settings.tpsYellowMin());
        Health msptHealth = atMost(mspt, settings.msptGreenMax(), settings.msptYellowMax());
        Health pingHealth = atMost(ping, settings.pingGreenMax(), settings.pingYellowMax());
        Component title = messages.renderFor(player, "admin.tps-bar.title",
                "tps", tpsHealth.decimal(tps),
                "mspt", msptHealth.decimal(mspt),
                "ping", pingHealth.gradient(String.valueOf(ping)));
        double fill = (mspt - settings.msptEmpty()) / (settings.msptFull() - settings.msptEmpty());
        return new Display(title, msptHealth.barColor, (float) Math.clamp(fill, 0.0, 1.0));
    }

    private static Health atMost(double value, double greenMax, double yellowMax) {
        return value <= greenMax ? Health.GREEN : value <= yellowMax ? Health.YELLOW : Health.RED;
    }

    private static Health atLeast(double value, double greenMin, double yellowMin) {
        return value >= greenMin ? Health.GREEN : value >= yellowMin ? Health.YELLOW : Health.RED;
    }

    private enum Health {
        GREEN(NamedTextColor.GREEN, NamedTextColor.DARK_GREEN, BossBar.Color.GREEN), YELLOW(NamedTextColor.YELLOW,
                TextColor.color(0xFFAA00),
                BossBar.Color.YELLOW), RED(NamedTextColor.RED, NamedTextColor.DARK_RED, BossBar.Color.RED);

        private final NamedTextColor textColor;
        private final TextColor darkColor;
        private final BossBar.Color barColor;

        Health(NamedTextColor textColor, TextColor darkColor, BossBar.Color barColor) {
            this.textColor = textColor;
            this.darkColor = darkColor;
            this.barColor = barColor;
        }

        private Component decimal(double value) {
            String formatted = String.format(Locale.ROOT, "%.2f", value);
            int dot = formatted.indexOf('.');
            if (dot < 0) {
                return Component.text(formatted, textColor);
            }
            return Component.text(formatted.substring(0, dot), textColor)
                    .append(gradient(formatted.substring(dot)));
        }

        /** Inclusive light-to-dark endpoints; a single digit keeps the light color. */
        private Component gradient(String text) {
            var result = Component.text().color(textColor);
            for (int i = 0; i < text.length(); i++) {
                float fraction = text.length() == 1 ? 0.0f : (float) i / (text.length() - 1);
                result.append(
                        Component.text(String.valueOf(text.charAt(i)), TextColor.lerp(fraction, textColor, darkColor)));
            }
            return result.build();
        }
    }

    private record Display(Component title, BossBar.Color color, float progress) {
    }

    private record View(Player player, BossBar bar) {
        void hide() {
            player.hideBossBar(bar);
        }
    }
}
