package de.kreiscraft.modules.api.verification;

import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe state store for browser↔player link requests.
 *
 * <p>Tracks pending verification requests, confirmed links, and status. A background scrub task
 * runs every {@code timeout/4} to expire pending requests whose expiry time has passed.
 *
 * <p>All compound state mutations use {@link ConcurrentHashMap#compute},
 * {@link ConcurrentHashMap#computeIfPresent}, and {@link ConcurrentHashMap#remove(Object, Object)}
 * to remain consistent without external synchronisation.
 */
public final class VerificationService {

    /** Result of {@link #sendVerification}. */
    public enum SendResult {
        SENT, PLAYER_OFFLINE, ALREADY_PENDING
    }

    // -----------------------------------------------------------------------
    // Internal record
    // -----------------------------------------------------------------------

    record PendingRequest(UUID playerUuid, String playerName, Instant expiresAt) {}

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final Map<UUID, PendingRequest> pending = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> verifiedUsers = new ConcurrentHashMap<>();
    private final Map<UUID, VerificationStatus> statuses = new ConcurrentHashMap<>();

    private final Duration timeout;
    private final Clock clock;
    private final Scheduler.Task scrubTask;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public VerificationService(Scheduler scheduler, Duration timeout, Clock clock) {
        this.timeout = timeout;
        this.clock = clock;
        Duration period = timeout.dividedBy(4);
        this.scrubTask = scheduler.runRepeating(period, period, this::scrubExpired);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Records a pending link request between the browser UUID and the named Bukkit player.
     *
     * <p>The caller must have already obtained the target player via
     * {@code Bukkit.getPlayerExact(name)} on the main thread and passed the UUID here — this
     * service does NOT touch Bukkit API directly.
     *
     * @param dispatchPromptOnMain runnable that opens the {@link LinkGuiPrompt} on the main thread;
     *     only invoked when the request is freshly recorded (i.e., result is {@link
     *     SendResult#SENT}).
     */
    public SendResult sendVerification(
            UUID browserUuid, String playerName, UUID playerUuid, Runnable dispatchPromptOnMain) {
        // Defensive null guard: null playerUuid means the player is offline (caller was unable to
        // resolve the player on the main thread). Return PLAYER_OFFLINE immediately so the enum
        // value is reachable and callers get a meaningful result rather than an NPE.
        if (playerUuid == null) {
            return SendResult.PLAYER_OFFLINE;
        }

        Instant expiresAt = clock.instant().plus(timeout);
        PendingRequest request = new PendingRequest(playerUuid, playerName, expiresAt);

        // Use compute() so that placing the pending entry and writing the PENDING status happen
        // inside a single ConcurrentHashMap bin lock for this key. This closes the window where a
        // concurrent getStatus(browserUuid) call could observe a pending entry without a
        // corresponding status entry.
        //
        // dispatchPromptOnMain is intentionally called OUTSIDE compute() to avoid holding the bin
        // lock across a caller-supplied (potentially blocking) Bukkit-facing runnable.
        final AtomicBoolean firstInsert = new AtomicBoolean(false);
        pending.compute(
                browserUuid,
                (k, existing) -> {
                    if (existing != null) {
                        return existing; // already pending — leave unchanged
                    }
                    statuses.put(k, VerificationStatus.PENDING);
                    firstInsert.set(true);
                    return request;
                });

        if (!firstInsert.get()) {
            return SendResult.ALREADY_PENDING;
        }

        dispatchPromptOnMain.run();
        return SendResult.SENT;
    }

    /**
     * Called when the target player clicks "Accept" in the {@link LinkGuiPrompt}.
     * Atomically moves the pending request to the accepted state.
     */
    public void confirmVerification(UUID browserUuid) {
        pending.computeIfPresent(
                browserUuid,
                (k, req) -> {
                    verifiedUsers.put(k, req.playerUuid());
                    statuses.put(k, VerificationStatus.ACCEPTED);
                    return null; // remove from pending
                });
    }

    /**
     * Called when the target player clicks "Decline" in the {@link LinkGuiPrompt}.
     * Atomically removes the pending request and marks the status as DECLINED.
     */
    public void cancelVerification(UUID browserUuid) {
        pending.computeIfPresent(
                browserUuid,
                (k, req) -> {
                    statuses.put(k, VerificationStatus.DECLINED);
                    return null; // remove from pending
                });
    }

    /**
     * Returns the UUID of the Bukkit player linked to this browser UUID, or {@code null} if not
     * verified.
     */
    public UUID getLinkedPlayer(UUID browserUuid) {
        return verifiedUsers.get(browserUuid);
    }

    /**
     * Returns the current {@link VerificationStatus} for the browser UUID, or {@code null} if no
     * entry exists.
     */
    public VerificationStatus getStatus(UUID browserUuid) {
        return statuses.get(browserUuid);
    }

    /**
     * Removes the ACCEPTED/DECLINED entry for the given browser UUID.
     *
     * @return the previously linked player UUID, or {@code null} if none.
     */
    public UUID unlink(UUID browserUuid) {
        statuses.remove(browserUuid);
        return verifiedUsers.remove(browserUuid);
    }

    /** Returns the number of currently pending verification requests. */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * Cancels the background scrub task. Idempotent — calling twice does not throw.
     */
    public void shutdown() {
        scrubTask.cancel();
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    /** Removes expired pending requests and marks them as DECLINED. */
    private void scrubExpired() {
        Instant now = clock.instant();
        for (Map.Entry<UUID, PendingRequest> entry : pending.entrySet()) {
            UUID browserUuid = entry.getKey();
            PendingRequest req = entry.getValue();
            if (req.expiresAt().isBefore(now)) {
                // CAS-style removal: only remove if the value is still the same request we saw
                if (pending.remove(browserUuid, req)) {
                    statuses.put(browserUuid, VerificationStatus.DECLINED);
                }
            }
        }
    }
}
