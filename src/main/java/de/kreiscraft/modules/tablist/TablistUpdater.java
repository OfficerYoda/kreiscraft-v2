package de.kreiscraft.modules.tablist;

import de.kreiscraft.core.message.PlaceholderResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Holds the shared repeating-task logic for updating the player-list header and footer.
 * One instance is scheduled once; it iterates all online players on each tick.
 * Templates can be swapped atomically on config reload without rescheduling.
 */
public final class TablistUpdater {

    private final Server server;
    private final PlaceholderResolver resolver;
    private volatile String headerTemplate;
    private volatile String footerTemplate;

    public TablistUpdater(
            Server server,
            PlaceholderResolver resolver,
            String headerTemplate,
            String footerTemplate) {
        this.server = server;
        this.resolver = resolver;
        this.headerTemplate = headerTemplate;
        this.footerTemplate = footerTemplate;
    }

    /**
     * Replaces the header and footer templates; the change takes effect on the next tick.
     */
    public void templates(String headerTemplate, String footerTemplate) {
        this.headerTemplate = headerTemplate;
        this.footerTemplate = footerTemplate;
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
        String headerSource = this.headerTemplate;
        String footerSource = this.footerTemplate;
        MiniMessage mm = MiniMessage.miniMessage();
        Component header = mm.deserialize(resolver.resolveString(headerSource, player));
        Component footer = mm.deserialize(resolver.resolveString(footerSource, player));
        player.sendPlayerListHeaderAndFooter(header, footer);
    }
}
