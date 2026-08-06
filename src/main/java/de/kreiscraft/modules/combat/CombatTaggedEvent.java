package de.kreiscraft.modules.combat;

import java.time.Duration;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when a player's combat tag changes — on initial tag, re-tag, or expiry/clear. */
public final class CombatTaggedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uuid;
    private final Duration before;
    private final Duration after;

    /**
     * @param uuid   player whose combat tag changed
     * @param before remaining duration before this change (ZERO if not previously tagged)
     * @param after  new remaining duration (ZERO on clear/expiry)
     */
    public CombatTaggedEvent(UUID uuid, Duration before, Duration after) {
        this.uuid = uuid;
        this.before = before;
        this.after = after;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Duration getBefore() {
        return before;
    }

    public Duration getAfter() {
        return after;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
