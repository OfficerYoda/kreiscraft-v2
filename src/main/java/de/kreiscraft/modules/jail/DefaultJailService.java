package de.kreiscraft.modules.jail;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.ServerConfigSchema;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Default in-process implementation of {@link JailService}.
 *
 * <p>Jail entries and their restore locations stay Activity Data in {@link JailData}. The jail
 * location itself is operator-owned Server Configuration: it is read live from and written to
 * {@code config.yml} through {@link ConfigService} so {@code /setjail} cannot leave the running
 * server and the durable file disagreeing.
 */
public final class DefaultJailService implements JailService {

    private static final String MODULE = "jail";
    private static final String LOCATION = "location";

    private final JailData data;
    private final ConfigService config;

    public DefaultJailService(JailData data, ConfigService config) {
        this.data = data;
        this.config = config;
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

    @Override
    public Optional<Location> jailLocation() {
        ConfigurationNode node = config.module(MODULE).node(LOCATION);
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(node.get(Location.class));
        } catch (SerializationException e) {
            throw new IllegalStateException("invalid jail location in Server Configuration", e);
        }
    }

    @Override
    public void setJailLocation(Location location) {
        config.applyEdit(ServerConfigSchema.jailLocation(), location);
    }
}
