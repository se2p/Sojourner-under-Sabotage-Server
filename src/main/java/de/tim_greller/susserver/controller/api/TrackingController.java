package de.tim_greller.susserver.controller.api;

import de.tim_greller.susserver.dto.ClientTrackEventDTO;
import de.tim_greller.susserver.service.tracking.UserEventTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives client-reported UI interactions (debugger buttons, breakpoints, ...)
 * and records them via the same tracking pipeline as server-side events.
 */
@RestController
@RequiredArgsConstructor
public class TrackingController {

    private final UserEventTrackingService trackingService;

    @PostMapping("${paths.api}/track")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void track(@RequestBody ClientTrackEventDTO event) {
        if (event.getEventType() == null || event.getEventType().isBlank()) {
            return;
        }
        // "ui-" marks these as client-reported (spoofable) to keep them distinct
        // from the trusted server-authored event types.
        trackingService.trackEvent("ui-" + event.getEventType(), event.getDetails());
    }
}