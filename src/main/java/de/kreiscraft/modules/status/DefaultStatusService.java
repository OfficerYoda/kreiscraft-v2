package de.kreiscraft.modules.status;

import java.util.UUID;
import org.bukkit.plugin.PluginManager;

public final class DefaultStatusService implements StatusService {

    private final PluginManager events;
    private final StatusData data;

    public DefaultStatusService(PluginManager events, StatusData data) {
        this.events = events;
        this.data = data;
    }

    @Override
    public Status get(UUID playerId) {
        return data.get().getOrDefault(playerId, Status.defaults());
    }

    @Override
    public void setPrefix(UUID playerId, String prefixMiniMessage) {
        Status before = get(playerId);
        if (before.prefix().equals(prefixMiniMessage)) {
            return;
        }
        Status after = new Status(prefixMiniMessage, before.suffix(), before.color());
        data.update(m -> m.put(playerId, after));
        events.callEvent(new StatusChangedEvent(playerId, before, after));
    }

    @Override
    public void setSuffix(UUID playerId, String suffixMiniMessage) {
        Status before = get(playerId);
        if (before.suffix().equals(suffixMiniMessage)) {
            return;
        }
        Status after = new Status(before.prefix(), suffixMiniMessage, before.color());
        data.update(m -> m.put(playerId, after));
        events.callEvent(new StatusChangedEvent(playerId, before, after));
    }

    @Override
    public void setColor(UUID playerId, int argb) {
        Status before = get(playerId);
        if (before.color() == argb) {
            return;
        }
        Status after = new Status(before.prefix(), before.suffix(), argb);
        data.update(m -> m.put(playerId, after));
        events.callEvent(new StatusChangedEvent(playerId, before, after));
    }

    @Override
    public Status defaults() {
        return Status.defaults();
    }
}
