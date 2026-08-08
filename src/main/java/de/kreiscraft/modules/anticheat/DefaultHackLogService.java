package de.kreiscraft.modules.anticheat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultHackLogService implements HackLogService {

    private final HackLogData data;
    private final Set<UUID> handshakePassed = ConcurrentHashMap.newKeySet();

    public DefaultHackLogService(HackLogData data) {
        this.data = data;
    }

    @Override
    public void record(HackEntry entry) {
        data.update(map -> map.computeIfAbsent(entry.playerId(), k -> new ArrayList<>()).add(entry));
    }

    @Override
    public List<HackEntry> forPlayer(UUID uuid, HackEntry.Kind kind) {
        List<HackEntry> all = data.get().get(uuid);
        if (all == null) {
            return Collections.emptyList();
        }
        List<HackEntry> result = new ArrayList<>();
        for (HackEntry e : all) {
            if (e.kind() == kind) {
                result.add(e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Marks a player as having completed the mod-checker handshake. */
    void markHandshakePassed(UUID playerId) {
        handshakePassed.add(playerId);
    }

    /** Returns true if the player has completed the mod-checker handshake. */
    boolean hasHandshakePassed(UUID playerId) {
        return handshakePassed.contains(playerId);
    }

    /** Cleans up handshake state when a player disconnects. */
    void clearHandshake(UUID playerId) {
        handshakePassed.remove(playerId);
    }
}
