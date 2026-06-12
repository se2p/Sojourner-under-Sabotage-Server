package de.tim_greller.susserver.controller.ws;

import java.security.Principal;
import java.util.List;

import de.tim_greller.susserver.events.Event;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.game.ActiveGameModeService;
import de.tim_greller.susserver.service.game.EventBufferService;
import de.tim_greller.susserver.service.game.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Slf4j
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UserService userService;
    private final ActiveGameModeService activeModeService;
    private final EventBufferService eventBufferService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/events")  // complete endpoint depends on configured application message handler prefix: /app/events
    public void handleClientEvent(Event clientEvent, Principal principal,
                                  @Header(name = "game-mode", required = false) String gameMode) {
        log.info("client event [{}], timestamp: {}, user: {}, mode: {}",
                clientEvent.getClass().getSimpleName(),
                clientEvent.getTimestamp(),
                principal.getName(),
                gameMode
        );

        // Spring security context is not available for STOMP messages
        userService.overridePrincipal(principal);

        // The sending page states which strand it plays; bind it for this message's handling
        activeModeService.bindMode(gameMode);
        try {
            eventService.handleEvent(clientEvent);
        } finally {
            activeModeService.clearMode();
        }
    }

    @GetMapping(value = "${paths.api}/resend-events/{sinceTimestamp}", produces = "text/plain")
    public ResponseEntity<String> resendEvents(@PathVariable long sinceTimestamp) {
        final String username = userService.requireCurrentUserId();
        final List<Event> events = eventBufferService.replaySince(username, sinceTimestamp);
        if (events.isEmpty()) {
            log.info("No events found for user {}", username);
            return ResponseEntity.ok("No events found for user " + username);
        }
        events.forEach(event -> {
            log.info("Resending event {} for user {}", event.getClass().getSimpleName(), username);
            simpMessagingTemplate.convertAndSendToUser(username, "/queue/events", event);
        });
        log.info("Resent all events since {}", sinceTimestamp);
        return ResponseEntity.ok("Resent all events since " + sinceTimestamp);
    }
}