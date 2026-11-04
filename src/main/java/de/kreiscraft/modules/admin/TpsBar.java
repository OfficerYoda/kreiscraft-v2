package de.kreiscraft.modules.admin;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

/** Session-only, per-player performance bars. All access happens on the server thread. */
public final class TpsBar implements PriorityListener {

    private final MessageService messages;
    private final PlaceholderResolver resolver;
    private final Map<UUID, View> views = new HashMap<>();

    public TpsBar(MessageService messages, PlaceholderResolver resolver) {
        this.messages = messages;
        this.resolver = resolver;
    }

    /** Toggles the player's bar and returns whether it is now visible. */
    public boolean toggle(Player player) {
        View previous = views.remove(player.getUniqueId());
        if (previous != null) {
            previous.hide();
            return false;
        }
        BossBar bar = BossBar.bossBar(title(player), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
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
                view.bar().name(title(view.player()));
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

    private Component title(Player player) {
        return messages.renderFor(player, "admin.tps-bar.title",
                "tps", resolver.resolveString("%tps%", player),
                "mspt", resolver.resolveString("%mspt%", player),
                "ping", resolver.resolveString("%ping%", player));
    }

    private record View(Player player, BossBar bar) {
        void hide() {
            player.hideBossBar(bar);
        }
    }
}
