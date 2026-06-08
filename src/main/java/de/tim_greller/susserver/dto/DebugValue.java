package de.tim_greller.susserver.dto;

import java.util.Map;

// A single variable value: type, string preview and nested children for compound values
public record DebugValue(
        String type,
        String preview,
        Map<String, DebugValue> children
) {
}