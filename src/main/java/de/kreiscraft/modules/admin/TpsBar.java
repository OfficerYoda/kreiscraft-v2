package de.kreiscraft.modules.admin;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

/** Session-only, per-player performance bars. All access happens on the server thread. */
public final class TpsBar implements PriorityListener {

    private final MessageService messages;
    private final Server server;
    private final Supplier<TpsBarConfig> config;
    private final Map<UUID, View> views = new HashMap<>();

    public TpsBar(MessageService messages, Server server, Supplier<TpsBarConfig> config) {
        this.messages = messages;
        this.server = server;
        this.config = config;
    }

    /** Toggles the player's bar and returns whether it is now visible. */
    public boolean toggle(Player player) {
        View previous = views.remove(player.getUniqueId());
        if (previous != null) {
            previous.hide();
            return false;
        }
        Display display = display(player);
        BossBar bar = BossBar.bossBar(display.title(), display.progress(), display.color(), BossBar.Overlay.NOTCHED_20);
        views.put(player.getUniqueId(), new View(player, bar));
        player.showBossBar(bar);
        return true;
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
                "tps", Component.text(String.format(Locale.ROOT, "%.2f", tps), tpsHealth.textColor),
                "mspt", Component.text(String.format(Locale.ROOT, "%.2f", mspt), msptHealth.textColor),
                "ping", Component.text(String.valueOf(ping), pingHealth.textColor));
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
        GREEN(NamedTextColor.GREEN, BossBar.Color.GREEN), YELLOW(NamedTextColor.YELLOW,
                BossBar.Color.YELLOW), RED(NamedTextColor.RED, BossBar.Color.RED);

        private final NamedTextColor textColor;
        private final BossBar.Color barColor;

        Health(NamedTextColor textColor, BossBar.Color barColor) {
            this.textColor = textColor;
            this.barColor = barColor;
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
