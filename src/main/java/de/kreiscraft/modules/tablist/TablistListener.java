package de.kreiscraft.modules.tablist;

import de.kreiscraft.core.event.PriorityListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Applies the tab-list header/footer to a player as soon as they join, so the presentation is
 * correct before the next global update tick. This does not schedule any per-player task — the
 * shared {@link TablistUpdater} task keeps refreshing everyone on its interval.
 */
public final class TablistListener implements PriorityListener {

    private final TablistUpdater updater;

    public TablistListener(TablistUpdater updater) {
        this.updater = updater;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updater.sendTo(event.getPlayer());
    }
}
