package de.kreiscraft.shared.inventory;

import de.kreiscraft.platform.OfflinePlayerSnapshot;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A successful administrative edit, published on the server thread after atomic commit. */
public final class OfflineInventoryCommittedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final OfflinePlayerSnapshot snapshot;

    public OfflineInventoryCommittedEvent(UUID playerId, OfflinePlayerSnapshot snapshot) {
        this.playerId = playerId;
        this.snapshot = snapshot;
    }

    public UUID playerId() {
        return playerId;
    }

    public OfflinePlayerSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
