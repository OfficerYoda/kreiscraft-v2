package de.kreiscraft.shared.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class ChatBridgeTest {

    private static ChatBridgeEvent sample(String text) {
        return new ChatBridgeEvent(
                UUID.randomUUID(), "Alice", Component.text(text), text, Instant.now());
    }

    @Test
    void publish_deliversToAllSubscribers() {
        ChatBridge bridge = new ChatBridge();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        bridge.subscribe(e -> a.add(e.plainText()));
        bridge.subscribe(e -> b.add(e.plainText()));

        bridge.publish(sample("hi"));

        assertThat(a).containsExactly("hi");
        assertThat(b).containsExactly("hi");
    }

    @Test
    void unsubscribe_stopsDelivery() {
        ChatBridge bridge = new ChatBridge();
        List<String> a = new ArrayList<>();
        ChatBridge.Subscription sub = bridge.subscribe(e -> a.add(e.plainText()));

        bridge.publish(sample("one"));
        sub.unsubscribe();
        bridge.publish(sample("two"));

        assertThat(a).containsExactly("one");
        assertThat(bridge.subscriberCount()).isZero();
    }

    @Test
    void publish_survivesSubscriberException() {
        ChatBridge bridge = new ChatBridge();
        List<String> reached = new ArrayList<>();
        bridge.subscribe(
                e -> {
                    throw new RuntimeException("boom");
                });
        bridge.subscribe(e -> reached.add(e.plainText()));

        bridge.publish(sample("survive"));

        assertThat(reached).containsExactly("survive");
    }

    @Test
    void publish_isSafeFromMultipleThreads() throws InterruptedException {
        ChatBridge bridge = new ChatBridge();
        List<String> received = java.util.Collections.synchronizedList(new ArrayList<>());
        bridge.subscribe(e -> received.add(e.plainText()));

        int workers = 8;
        int perWorker = 100;
        CountDownLatch done = new CountDownLatch(workers);
        var pool = Executors.newFixedThreadPool(workers);
        for (int w = 0; w < workers; w++) {
            final int id = w;
            pool.execute(
                    () -> {
                        for (int i = 0; i < perWorker; i++) {
                            bridge.publish(sample("w" + id + "-" + i));
                        }
                        done.countDown();
                    });
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(received).hasSize(workers * perWorker);
    }
}
