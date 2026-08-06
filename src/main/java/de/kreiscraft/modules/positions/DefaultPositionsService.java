package de.kreiscraft.modules.positions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

public final class DefaultPositionsService implements PositionsService {

    private final PositionsData data;

    public DefaultPositionsService(PositionsData data) {
        this.data = data;
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
        data.update(
                m -> m.computeIfAbsent(uuid, k -> new LinkedHashMap<>())
                        .put(name, new PositionEntry(location)));
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
