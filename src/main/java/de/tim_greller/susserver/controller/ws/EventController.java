package de.tim_greller.susserver.controller.ws;

import java.security.Principal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.tim_greller.susserver.events.Event;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.game.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Slf4j
public class EventController {

    private static final long MAX_KEEP_EVENT_MILLIS = 20 * 60 * 1000;  // 20 minutes
    private static final long GARBAGE_COLLECT_INTERVAL_MILLIS = 5 * 60 * 1000;  // 5 minutes

    private final EventService eventService;
    private final UserService userService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final Map<String, List<Event>> eventsByUser = new HashMap<>();
    private long lastGarbageCollect = System.currentTimeMillis();

    public EventController(EventService eventService, UserService userService,
                           SimpMessagingTemplate simpMessagingTemplate) {
        this.eventService = eventService;
        this.userService = userService;
        this.simpMessagingTemplate = simpMessagingTemplate;

        eventService.setEventPublisher(this::sendEventToClient);
    }

    @MessageMapping("/events")  // complete endpoint depends on configured application message handler prefix: /app/events
    public void handleClientEvent(Event clientEvent, Principal principal) {
        log.info("client event [{}], timestamp: {}, user: {}",
                clientEvent.getClass().getSimpleName(),
                clientEvent.getTimestamp(),
                principal.getName()
        );

        // Spring security context is not available for STOMP messages
        userService.overridePrincipal(principal);

        eventService.handleEvent(clientEvent);
    }

    public void sendEventToClient(Event event) {
        final String username = userService.requireCurrentUserId();
        simpMessagingTemplate.convertAndSendToUser(username, "/queue/events", event);
        log.info("sent {} to user {}", event.getClass().getSimpleName(), username);
        eventsByUser.computeIfAbsent(username, key -> new LinkedList<>()).add(event);
        garbageCollectIfNeeded();
    }

    private void garbageCollectIfNeeded() {
        if (System.currentTimeMillis() - lastGarbageCollect > GARBAGE_COLLECT_INTERVAL_MILLIS) {
            garbageCollectEvents();
            lastGarbageCollect = System.currentTimeMillis();
        }
    }

    public void garbageCollectEvents() {
        final long now = System.currentTimeMillis();
        eventsByUser.forEach((key, events) -> {
            int sizeBefore = events.size();
            events.removeIf(event -> now - event.getTimestamp() > MAX_KEEP_EVENT_MILLIS);
            log.info("Garbage collected {} events of user '{}'", sizeBefore - events.size(), key);
        });
        eventsByUser.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @GetMapping(value = "${paths.api}/resend-events/{sinceTimestamp}", produces = "text/plain")
    public ResponseEntity<String> resendEvents(@PathVariable long sinceTimestamp) {
        final String username =  userService.requireCurrentUserId();
        if (!eventsByUser.containsKey(username)) {
            log.info("No events found for user {}", username);
            return ResponseEntity.ok("No events found for user " + username);
        }
        final List<Event> events = eventsByUser.get(username);
        events.stream().sorted(Comparator.comparingLong(Event::getTimestamp))
                .forEach(event -> {
                    if (event.getTimestamp() > sinceTimestamp) {
                        log.info("Resending event {} for user {}", event.getClass().getSimpleName(), username);
                        simpMessagingTemplate.convertAndSendToUser(username, "/queue/events", event);
                    } else {
                        // client already received this event as it is older (or same) as the requested timestamp
                        events.remove(event);
                    }
                });
        log.info("Resent all events since {}", sinceTimestamp);
        return ResponseEntity.ok("Resent all events since " + sinceTimestamp);
    }
}
