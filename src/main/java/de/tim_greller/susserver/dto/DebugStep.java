package de.tim_greller.susserver.dto;

import java.util.Map;

// One captured execution step: source line info plus a snapshot of all live variables
public record DebugStep(
        int globalIndex,
        int stepIndex,
        int lineNumber,
        String methodName,
        String testMethodName,
        Map<String, DebugValue> variables
) {
}