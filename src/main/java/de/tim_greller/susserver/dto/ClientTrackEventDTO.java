package de.tim_greller.susserver.dto;

import lombok.Data;

/**
 * A UI interaction reported by the client for analytics (e.g. debugger buttons,
 * breakpoint toggles). {@code details} is free-form and stored as JSON.
 */
@Data
public class ClientTrackEventDTO {
    private String eventType;
    private Object details;
}