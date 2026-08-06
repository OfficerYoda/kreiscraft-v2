package de.kreiscraft.modules.jail;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/** Default in-process implementation of {@link JailService}. */
public final class DefaultJailService implements JailService {

    private final JailData data;

    public DefaultJailService(JailData data) {
        this.data = data;
    }

    @Override
    public boolean isJailed(UUID uuid) {
        return data.get().containsKey(uuid);
    }

    @Override
    public void jail(UUID uuid, Location restoreLocation, String reason) {
        data.update(m -> m.put(uuid, new JailEntry(restoreLocation, reason, Instant.now())));
    }

    @Override
    public Optional<JailEntry> release(UUID uuid) {
        JailEntry[] holder = new JailEntry[1];
        data.update(m -> holder[0] = m.remove(uuid));
        return Optional.ofNullable(holder[0]);
    }

    @Override
    public Map<UUID, JailEntry> allJailed() {
        return Collections.unmodifiableMap(data.get());
    }
}
