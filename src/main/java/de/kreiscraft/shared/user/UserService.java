package de.kreiscraft.shared.user;

import de.kreiscraft.platform.PaperPlatform;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Holds live {@link User} instances keyed by UUID. Replaces the legacy {@code Kreiscraft.USERS}
 * public static map.
 *
 * <p>Storage uses a {@link ConcurrentHashMap} — reads are lock-free; writes occur on the main
 * thread via Bukkit event handlers.
 */
public final class UserService {

    private final PaperPlatform platform;
    private final Clock clock;
    private final Map<UUID, User> users = new ConcurrentHashMap<>();

    public UserService(PaperPlatform platform, Clock clock) {
        this.platform = platform;
        this.clock = clock;
    }

    /**
     * Registers a player. Idempotent — returns the existing {@link User} if already present.
     * Called from {@code UserListener} on {@code PlayerJoinEvent}.
     */
    public User join(Player player) {
        return users.computeIfAbsent(player.getUniqueId(), id -> new User(player, clock.instant()));
    }

    /**
     * Removes and returns the {@link User} for the given UUID. Called on {@code PlayerQuitEvent}.
     */
    public Optional<User> quit(UUID uuid) {
        User removed = users.remove(uuid);
        if (removed != null) {
            removed.detachPlayer();
        }
        return Optional.ofNullable(removed);
    }

    public Optional<User> get(UUID uuid) {
        return Optional.ofNullable(users.get(uuid));
    }

    public Optional<User> get(Player player) {
        return get(player.getUniqueId());
    }

    /** Returns a snapshot of all currently tracked users. */
    public Collection<User> all() {
        return List.copyOf(users.values());
    }

    /**
     * Walks every currently-online player and calls {@link #join(Player)} for each. Handles the
     * case where the plugin reloads while players are already online.
     */
    public void rehydrateOnlinePlayers() {
        for (Player p : platform.server().getOnlinePlayers()) {
            join(p);
        }
    }
}
