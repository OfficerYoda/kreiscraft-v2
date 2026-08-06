package de.kreiscraft.modules.status;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class StatusChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final Status before;
    private final Status after;

    public StatusChangedEvent(UUID playerId, Status before, Status after) {
        this.playerId = playerId;
        this.before = before;
        this.after = after;
    }

    public UUID playerId() {
        return playerId;
    }

    public Status before() {
        return before;
    }

    public Status after() {
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
