package de.tim_greller.susserver.service.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import de.tim_greller.susserver.events.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Per-user buffer of recently pushed events so clients can request a replay of
 * what they missed after a websocket reconnect.
 */
@Service
@Slf4j
public class EventBufferService {

    private static final long MAX_KEEP_EVENT_MILLIS = 20 * 60 * 1000;  // 20 minutes
    private static final long GARBAGE_COLLECT_INTERVAL_MILLIS = 5 * 60 * 1000;  // 5 minutes

    // The lists are only ever mutated inside compute callbacks, which the map runs atomically per key.
    private final ConcurrentHashMap<String, List<Event>> eventsByUser = new ConcurrentHashMap<>();
    private final AtomicLong lastGarbageCollect = new AtomicLong(System.currentTimeMillis());

    public void buffer(String username, Event event) {
        eventsByUser.compute(username, (key, events) -> {
            if (events == null) {
                events = new ArrayList<>();
            }
            events.add(event);
            return events;
        });
        garbageCollectIfNeeded();
    }

    /**
     * Returns the user's buffered events newer than the given timestamp, oldest first.
     * Events at or before the timestamp were already received by the client and are dropped.
     */
    public List<Event> replaySince(String username, long sinceTimestamp) {
        final List<Event> newerEvents = new ArrayList<>();
        eventsByUser.computeIfPresent(username, (key, events) -> {
            events.removeIf(event -> event.getTimestamp() <= sinceTimestamp);
            newerEvents.addAll(events);
            return events.isEmpty() ? null : events;
        });
        newerEvents.sort(Comparator.comparingLong(Event::getTimestamp));
        return newerEvents;
    }

    private void garbageCollectIfNeeded() {
        final long now = System.currentTimeMillis();
        final long last = lastGarbageCollect.get();
        // CAS so concurrent publishers don't garbage collect twice
        if (now - last > GARBAGE_COLLECT_INTERVAL_MILLIS && lastGarbageCollect.compareAndSet(last, now)) {
            garbageCollectEvents();
        }
    }

    private void garbageCollectEvents() {
        final long now = System.currentTimeMillis();
        for (String username : eventsByUser.keySet()) {
            eventsByUser.computeIfPresent(username, (key, events) -> {
                final int sizeBefore = events.size();
                events.removeIf(event -> now - event.getTimestamp() > MAX_KEEP_EVENT_MILLIS);
                log.info("Garbage collected {} events of user '{}'", sizeBefore - events.size(), key);
                return events.isEmpty() ? null : events;
            });
        }
    }
}
