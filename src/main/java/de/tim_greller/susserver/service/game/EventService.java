package de.tim_greller.susserver.service.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import de.tim_greller.susserver.events.Event;
import de.tim_greller.susserver.service.tracking.UserEventTrackingService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventService {

    private final UserEventTrackingService trackingService;

    private final ActiveGameModeService activeModeService;

    /**
     * Publishes an event to the client (the websocket transport).
     */
    private final EventPublisher eventPublisher;

    /**
     * Handlers that handle events coming from the client.
     */
    private final Map<Class<? extends Event>, List<Consumer<? extends Event>>> eventHandlers = new HashMap<>();

    /**
     * Registers a handler for a specific event type.
     *
     * @param eventType The type of the event to handle.
     * @param handler The handler for the event.
     * @param <T> The type of the event.
     */
    public <T extends Event> void registerHandler(@NonNull Class<T> eventType, @NonNull Consumer<T> handler) {
        eventHandlers.computeIfAbsent(eventType, key -> new ArrayList<>(1));
        eventHandlers.get(eventType).add(handler);
    }

    /**
     * Handles an event coming from the client.
     *
     * @param event The event to handle.
     * @param <T> The type of the event.
     */
    public <T extends Event> void handleEvent(@NonNull T event) {
        trackEvent(event);
        dispatchToHandlers(event);
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> void dispatchToHandlers(@NonNull T event) {
        Class<T> eventType = (Class<T>) event.getClass();
        if (eventHandlers.containsKey(eventType)) {
            List<Consumer<? extends Event>> handler = eventHandlers.get(eventType);
            for (Consumer<? extends Event> consumer : handler) {
                ((Consumer<T>) consumer).accept(event);
            }
        }
    }

    /**
     * Publishes an event to the client.
     *
     * @param event The event to publish.
     */
    public void publishEvent(@NonNull Event event) {
        trackEvent(event);
        eventPublisher.publish(event);
    }

    /**
     * Publishes an event to the client and handles it on the server side.
     *
     * @param event The event to publish and handle.
     * @param <T> The type of the event.
     */
    public <T extends Event> void publishAndHandleEvent(@NonNull T event) {
        trackEvent(event);
        dispatchToHandlers(event);
        eventPublisher.publish(event);
    }

    /**
     * Tags the event with the strand it belongs to and records it for analytics.
     * The mode must be set before tracking, so the persisted JSON carries it.
     */
    private void trackEvent(@NonNull Event event) {
        if (event.getMode() == null) {
            event.setMode(activeModeService.getModeForCurrentUser());
        }
        trackingService.trackEvent(event.getClass().getSimpleName(), event);
    }
}
