package de.tim_greller.susserver.dto;

import java.util.Map;

public record DebugStep(
        int globalIndex,
        int stepIndex,
        int lineNumber,
        String methodName,
        String testMethodName,
        Map<String, DebugValue> variables
) {
}