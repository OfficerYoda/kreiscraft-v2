package de.kreiscraft.shared.chat;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publish/subscribe bus for chat lines. The chat-format module publishes {@link ChatBridgeEvent}s;
 * the API module subscribes to forward them to connected WebSocket clients. Thread-safe: publish and
 * subscribe may be called from any thread.
 */
public final class ChatBridge {

    private static final Logger LOGGER = Logger.getLogger(ChatBridge.class.getName());

    private final CopyOnWriteArrayList<Consumer<ChatBridgeEvent>> subscribers =
            new CopyOnWriteArrayList<>();

    /** Handle returned by {@link #subscribe} to remove a subscriber. */
    public interface Subscription {
        void unsubscribe();
    }

    /**
     * Registers a subscriber that will receive all future events.
     *
     * @param subscriber the callback to invoke on each published event
     * @return a {@link Subscription} whose {@code unsubscribe()} removes the subscriber
     */
    public Subscription subscribe(Consumer<ChatBridgeEvent> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    /**
     * Publishes an event to all current subscribers. Safe to call from any thread. If a subscriber
     * throws, the exception is logged and delivery continues to the remaining subscribers.
     *
     * @param event the chat event to broadcast
     */
    public void publish(ChatBridgeEvent event) {
        for (Consumer<ChatBridgeEvent> sub : subscribers) {
            try {
                sub.accept(event);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "chat bridge subscriber threw", e);
            }
        }
    }

    /**
     * Returns the current number of active subscribers. Intended for tests and diagnostics.
     *
     * @return subscriber count
     */
    public int subscriberCount() {
        return subscribers.size();
    }
}
