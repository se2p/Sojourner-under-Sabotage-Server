package de.tim_greller.susserver.service.game;

import de.tim_greller.susserver.events.Event;

/**
 * Pushes a server-side event to the current user's client.
 * Implemented by the websocket transport layer.
 */
@FunctionalInterface
public interface EventPublisher {
    void publish(Event event);
}
