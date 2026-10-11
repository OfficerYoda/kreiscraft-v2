package de.kreiscraft.core.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MockSchedulerTest {

    @Test
    void runOnMain_runsImmediately() {
        MockScheduler sched = new MockScheduler();
        AtomicInteger counter = new AtomicInteger();
        sched.runOnMain(counter::incrementAndGet);
        assertThat(counter).hasValue(1);
    }

    @Test
    void runAsync_runsImmediately() {
        MockScheduler sched = new MockScheduler();
        AtomicInteger counter = new AtomicInteger();
        sched.runAsync(counter::incrementAndGet);
        assertThat(counter).hasValue(1);
    }

    @Test
    void runLater_firesAtDeadline() {
        MockScheduler sched = new MockScheduler();
        AtomicInteger counter = new AtomicInteger();
        sched.runLater(Duration.ofSeconds(5), counter::incrementAndGet);

        sched.advance(Duration.ofSeconds(4));
        assertThat(counter).hasValue(0);

        sched.advance(Duration.ofSeconds(1));
        assertThat(counter).hasValue(1);
    }

    @Test
    void runRepeating_firesPeriodically() {
        MockScheduler sched = new MockScheduler();
        AtomicInteger counter = new AtomicInteger();
        sched.runRepeating(Duration.ofSeconds(1), Duration.ofSeconds(2), counter::incrementAndGet);

        sched.advance(Duration.ofSeconds(1)); // first fire
        assertThat(counter).hasValue(1);

        sched.advance(Duration.ofSeconds(2)); // period
        assertThat(counter).hasValue(2);

        sched.advance(Duration.ofSeconds(4)); // 2 more
        assertThat(counter).hasValue(4);
    }

    @Test
    void cancel_stopsFutureFires() {
        MockScheduler sched = new MockScheduler();
        AtomicInteger counter = new AtomicInteger();
        Scheduler.Task task =
                sched.runRepeating(Duration.ofSeconds(1), Duration.ofSeconds(1), counter::incrementAndGet);

        sched.advance(Duration.ofSeconds(1));
        assertThat(counter).hasValue(1);

        task.cancel();
        assertThat(task.isCancelled()).isTrue();

        sched.advance(Duration.ofSeconds(10));
        assertThat(counter).hasValue(1);
    }
}
