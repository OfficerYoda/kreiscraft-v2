package de.kreiscraft.modules.tablist;

import de.kreiscraft.core.message.PlaceholderResolver;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Holds the shared repeating-task logic for updating the player-list header and footer.
 * One instance is scheduled once; it iterates all online players on each tick. The templates are
 * read live through the supplied {@link Supplier}, so a config reload takes effect on the next tick
 * without rescheduling.
 */
public final class TablistUpdater {

    private final Server server;
    private final PlaceholderResolver resolver;
    private final Supplier<TablistConfig> config;

    public TablistUpdater(Server server, PlaceholderResolver resolver, Supplier<TablistConfig> config) {
        this.server = server;
        this.resolver = resolver;
        this.config = config;
    }

    /**
     * Called by the scheduler on each interval. Sends resolved header/footer to every online player.
     */
    public void tick() {
        for (Player player : server.getOnlinePlayers()) {
            sendTo(player);
        }
    }

    /**
     * Resolves and sends the header/footer to a single player. Used both by {@link #tick()} and to
     * give a joining player the correct presentation immediately instead of waiting for the next
     * interval.
     */
    public void sendTo(Player player) {
        TablistConfig cfg = config.get();
        MiniMessage mm = MiniMessage.miniMessage();
        Component header = mm.deserialize(resolver.resolveString(cfg.headerTemplate(), player));
        Component footer = mm.deserialize(resolver.resolveString(cfg.footerTemplate(), player));
        player.sendPlayerListHeaderAndFooter(header, footer);
    }
}
