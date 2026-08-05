package de.kreiscraft.shared.user;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Mutable per-player state envelope. Replaces the legacy {@code Kreiscraft.USERS} public static
 * map. Feature modules attach their own per-player state via {@link #setAttribute(Class, Object)}
 * keyed on a record class they own.
 */
public final class User {

    private final UUID uuid;
    private final String name;
    private final Instant joinedAt;
    private volatile Player player;
    private final Map<Class<?>, Object> attributes = new ConcurrentHashMap<>();

    public User(Player player, Instant joinedAt) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.joinedAt = joinedAt;
        this.player = player;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Player player() {
        return player;
    }

    /**
     * Called by {@link UserService#quit(java.util.UUID)} — the User survives briefly for the quit
     * event but its {@link #player()} reference is cleared.
     */
    void detachPlayer() {
        this.player = null;
    }

    public <T> Optional<T> attribute(Class<T> key) {
        return Optional.ofNullable(key.cast(attributes.get(key)));
    }

    public <T> void setAttribute(Class<T> key, T value) {
        attributes.put(key, value);
    }

    public <T> Optional<T> removeAttribute(Class<T> key) {
        return Optional.ofNullable(key.cast(attributes.remove(key)));
    }
}
