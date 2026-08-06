package de.kreiscraft.modules.bedrock;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Default implementation of {@link BedrockService}.
 *
 * <p>Thread-safety note: {@link #seen} and {@link #knownPlayers} are safe to call from
 * an async context because {@link BedrockData#update} operates on a {@link java.util.HashMap}
 * that is always mutated via {@link BedrockData#update}, which is called on the main thread
 * (the caller wraps it in {@code Scheduler.runOnMain}).
 */
public final class DefaultBedrockService implements BedrockService {

    private final BedrockData data;
    private volatile BedrockConfig config;

    public DefaultBedrockService(BedrockData data, BedrockConfig config) {
        this.data = data;
        this.config = config;
    }

    @Override
    public boolean isBedrock(String playerName) {
        return playerName.startsWith(config.prefix());
    }

    @Override
    public void seen(String name, UUID uuid) {
        data.update(map -> map.computeIfAbsent(uuid,
                id -> new BedrockRecord(name, id, Instant.now())));
    }

    @Override
    public Collection<BedrockRecord> knownPlayers() {
        return Collections.unmodifiableCollection(data.get().values());
    }

    /** Called by {@link BedrockModule#onConfigReload} to propagate updated config. */
    public void updateConfig(BedrockConfig config) {
        this.config = config;
    }
}
