package de.tim_greller.susserver.controller.ws;

import de.tim_greller.susserver.events.Event;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.game.ActiveGameModeService;
import de.tim_greller.susserver.service.game.EventBufferService;
import de.tim_greller.susserver.service.game.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends server-side events to the current user over the websocket and buffers
 * them for replay after a reconnect.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventPublisher implements EventPublisher {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserService userService;
    private final ActiveGameModeService activeModeService;
    private final EventBufferService eventBufferService;

    @Override
    public void publish(Event event) {
        final String username = userService.requireCurrentUserId();
        // Tag the event with the strand it belongs to so each tab can filter its own.
        if (event.getMode() == null) {
            event.setMode(activeModeService.getModeForCurrentUser());
        }
        simpMessagingTemplate.convertAndSendToUser(username, "/queue/events", event);
        log.info("sent {} to user {}", event.getClass().getSimpleName(), username);
        eventBufferService.buffer(username, event);
    }
}
