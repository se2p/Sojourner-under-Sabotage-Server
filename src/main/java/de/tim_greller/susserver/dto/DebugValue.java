package de.tim_greller.susserver.dto;

import java.util.Map;

public record DebugValue(
        String type,
        String preview,
        Map<String, DebugValue> children
) {
}