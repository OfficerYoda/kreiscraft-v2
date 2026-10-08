package de.kreiscraft.modules.positions;

import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

public final class DefaultPositionsService implements PositionsService {

    private final PositionsData data;
    private final Clock clock;

    public DefaultPositionsService(PositionsData data) {
        this(data, Clock.systemUTC());
    }

    DefaultPositionsService(PositionsData data, Clock clock) {
        this.data = data;
        this.clock = clock;
    }

    @Override
    public Map<String, PositionEntry> forPlayer(UUID uuid) {
        Map<String, PositionEntry> positions = data.get().get(uuid);
        if (positions == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(positions);
    }

    @Override
    public Optional<PositionEntry> get(UUID uuid, String name) {
        Map<String, PositionEntry> positions = data.get().get(uuid);
        if (positions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(positions.get(name));
    }

    @Override
    public void set(UUID uuid, String name, Location location) {
        set(uuid, name, location, uuid, null);
    }

    @Override
    public void set(UUID uuid, String name, Location location, UUID savedBy, String savedByName) {
        data.update(
                m -> m.computeIfAbsent(uuid, k -> new LinkedHashMap<>())
                        .put(name, new PositionEntry(location, savedBy, savedByName, clock.instant())));
    }

    @Override
    public void remove(UUID uuid, String name) {
        data.update(
                m -> {
                    Map<String, PositionEntry> positions = m.get(uuid);
                    if (positions != null) {
                        positions.remove(name);
                    }
                });
    }
}
