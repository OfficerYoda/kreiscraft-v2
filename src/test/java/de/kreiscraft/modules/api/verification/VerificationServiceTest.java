package de.kreiscraft.modules.api.verification;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.testsupport.MockScheduler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VerificationServiceTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private static final Instant BASE = Instant.parse("2026-08-06T00:00:00Z");

    // -----------------------------------------------------------------------
    // Test 1: sendVerification with no prior request returns SENT and calls dispatch
    // -----------------------------------------------------------------------

    @Test
    void sendVerification_whenNoPriorRequest_returnsSent_andCallsDispatch() {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        UUID browser = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        AtomicBoolean dispatched = new AtomicBoolean(false);

        VerificationService.SendResult result =
                service.sendVerification(browser, "PlayerOne", player, () -> dispatched.set(true));

        assertThat(result).isEqualTo(VerificationService.SendResult.SENT);
        assertThat(dispatched.get()).isTrue();
        assertThat(service.getStatus(browser)).isEqualTo(VerificationStatus.PENDING);
        assertThat(service.pendingCount()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Test 2: sendVerification when already pending returns ALREADY_PENDING and does not call dispatch again
    // -----------------------------------------------------------------------

    @Test
    void sendVerification_whenAlreadyPending_returnsAlreadyPending_andDoesNotCallDispatchAgain() {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        UUID browser = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        AtomicInteger dispatchCount = new AtomicInteger(0);

        service.sendVerification(browser, "PlayerOne", player, dispatchCount::incrementAndGet);
        VerificationService.SendResult result =
                service.sendVerification(
                        browser, "PlayerOne", player, dispatchCount::incrementAndGet);

        assertThat(result).isEqualTo(VerificationService.SendResult.ALREADY_PENDING);
        assertThat(dispatchCount.get()).isEqualTo(1);
        assertThat(service.pendingCount()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Test 3: confirmVerification moves pending to ACCEPTED
    // -----------------------------------------------------------------------

    @Test
    void confirmVerification_movesPendingToAccepted() {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        UUID browser = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        service.sendVerification(browser, "PlayerOne", player, () -> {
        });

        service.confirmVerification(browser);

        assertThat(service.getStatus(browser)).isEqualTo(VerificationStatus.ACCEPTED);
        assertThat(service.pendingCount()).isEqualTo(0);
        assertThat(service.getLinkedPlayer(browser)).isEqualTo(player);
    }

    // -----------------------------------------------------------------------
    // Test 4: confirmVerification when no pending entry does nothing
    // -----------------------------------------------------------------------

    @Test
    void confirmVerification_whenNoPending_doesNothing() {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        UUID browser = UUID.randomUUID();

        // Should not throw
        service.confirmVerification(browser);

        assertThat(service.getStatus(browser)).isNull();
        assertThat(service.getLinkedPlayer(browser)).isNull();
    }

    // -----------------------------------------------------------------------
    // Test 5: cancelVerification removes pending and sets status DECLINED
    // -----------------------------------------------------------------------

    @Test
    void cancelVerification_removesPending_andSetsStatusDeclined() {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        UUID browser = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        service.sendVerification(browser, "PlayerOne", player, () -> {
        });

        service.cancelVerification(browser);

        assertThat(service.getStatus(browser)).isEqualTo(VerificationStatus.DECLINED);
        assertThat(service.pendingCount()).isEqualTo(0);
        assertThat(service.getLinkedPlayer(browser)).isNull();
    }

    // -----------------------------------------------------------------------
    // Test 6: getLinkedPlayer returns mapped UUID after confirm
    // -----------------------------------------------------------------------

    @Test
    void getLinkedPlayer_returnsMappedUuid_afterConfirm() {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        UUID browser = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        service.sendVerification(browser, "PlayerOne", player, () -> {
        });
        service.confirmVerification(browser);

        assertThat(service.getLinkedPlayer(browser)).isEqualTo(player);
    }

    // -----------------------------------------------------------------------
    // Test 7: getLinkedPlayer returns null before confirm
    // -----------------------------------------------------------------------

    @Test
    void getLinkedPlayer_returnsNull_beforeConfirm() {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        UUID browser = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        service.sendVerification(browser, "PlayerOne", player, () -> {
        });

        assertThat(service.getLinkedPlayer(browser)).isNull();
    }

    // -----------------------------------------------------------------------
    // Test 8: unlink removes both mappings and returns previous player UUID
    // -----------------------------------------------------------------------

    @Test
    void unlink_removesBothMappings_andReturnsPreviousPlayerUuid() {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        UUID browser = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        service.sendVerification(browser, "PlayerOne", player, () -> {
        });
        service.confirmVerification(browser);

        UUID returned = service.unlink(browser);

        assertThat(returned).isEqualTo(player);
        assertThat(service.getLinkedPlayer(browser)).isNull();
        assertThat(service.getStatus(browser)).isNull();
    }

    // -----------------------------------------------------------------------
    // Test 9: scrubExpired after timeout removes pending and marks DECLINED
    // -----------------------------------------------------------------------

    @Test
    void scrubExpired_afterTimeout_removesPending_andMarksDeclined() {
        MockScheduler scheduler = new MockScheduler();
        // Start clock at BASE; we will advance via Clock.offset to simulate time passing
        // Use an array to hold a mutable clock reference
        Clock[] clockHolder = {Clock.fixed(BASE, ZoneOffset.UTC)};

        // We need a clock that advances with the scheduler — use a delegating wrapper
        // that checks the holder
        Clock delegatingClock =
                new Clock() {
                    @Override
                    public ZoneOffset getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return clockHolder[0].instant();
                    }
                };

        VerificationService service =
                new VerificationService(scheduler, TIMEOUT, delegatingClock);

        UUID browser = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        service.sendVerification(browser, "PlayerOne", player, () -> {
        });

        assertThat(service.getStatus(browser)).isEqualTo(VerificationStatus.PENDING);

        // Advance the clock past timeout so scrubExpired fires correctly
        clockHolder[0] = Clock.fixed(BASE.plus(TIMEOUT).plusSeconds(1), ZoneOffset.UTC);

        // Advance the mock scheduler past the timeout so the scrub task fires
        // Period = TIMEOUT / 4 = 30s; we need to advance enough to fire
        scheduler.advance(TIMEOUT.plusSeconds(1));

        assertThat(service.getStatus(browser)).isEqualTo(VerificationStatus.DECLINED);
        assertThat(service.pendingCount()).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Test 10: concurrent send and confirm never throws, converges to ACCEPTED
    // -----------------------------------------------------------------------

    @Test
    void concurrentSendAndConfirm_neverThrowsAndConvergesToOneState() throws InterruptedException {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        int numRequests = 1000;
        var pool = Executors.newFixedThreadPool(16);
        var latch = new CountDownLatch(1);
        UUID[] browsers = new UUID[numRequests];
        UUID[] players = new UUID[numRequests];

        for (int i = 0; i < numRequests; i++) {
            browsers[i] = UUID.randomUUID();
            players[i] = UUID.randomUUID();
        }

        for (int i = 0; i < numRequests; i++) {
            final int idx = i;
            pool.submit(
                    () -> {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        service.sendVerification(
                                browsers[idx], "P", players[idx], () -> {
                                });
                        service.confirmVerification(browsers[idx]);
                    });
        }

        latch.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // Every browser must end up ACCEPTED (no exception thrown)
        for (int i = 0; i < numRequests; i++) {
            assertThat(service.getStatus(browsers[i]))
                    .as("browser[%d] should be ACCEPTED", i)
                    .isEqualTo(VerificationStatus.ACCEPTED);
        }
    }

    // -----------------------------------------------------------------------
    // Test 11: sendVerification with null playerUuid returns PLAYER_OFFLINE, no dispatch
    // -----------------------------------------------------------------------

    @Test
    void sendVerification_whenPlayerUuidIsNull_returnsPlayerOffline_andDoesNotCallDispatch() {
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        AtomicBoolean dispatched = new AtomicBoolean(false);
        VerificationService.SendResult result =
                service.sendVerification(UUID.randomUUID(), "Alice", null, () -> dispatched.set(true));

        assertThat(result).isEqualTo(VerificationService.SendResult.PLAYER_OFFLINE);
        assertThat(dispatched.get()).isFalse();
        assertThat(service.pendingCount()).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Test 12: sendVerification places pending entry and status atomically
    // -----------------------------------------------------------------------

    @Test
    void sendVerification_placesPendingAndStatusAtomically() {
        // Verify that after sendVerification returns SENT, getStatus already reports PENDING.
        // Locks in the invariant that both writes (pending entry + status) happen before return.
        MockScheduler scheduler = new MockScheduler();
        Clock clock = Clock.fixed(BASE, ZoneOffset.UTC);
        VerificationService service = new VerificationService(scheduler, TIMEOUT, clock);

        var browserId = UUID.randomUUID();
        VerificationService.SendResult result =
                service.sendVerification(browserId, "Alice", UUID.randomUUID(), () -> {
                });

        assertThat(result).isEqualTo(VerificationService.SendResult.SENT);
        assertThat(service.getStatus(browserId)).isEqualTo(VerificationStatus.PENDING);
    }
}
