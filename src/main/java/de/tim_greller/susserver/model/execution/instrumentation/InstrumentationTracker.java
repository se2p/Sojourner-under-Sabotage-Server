package de.tim_greller.susserver.model.execution.instrumentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static de.tim_greller.susserver.util.Utils.filterMap;
import static de.tim_greller.susserver.util.Utils.mapMap;

import de.tim_greller.susserver.dto.DebugStep;
import de.tim_greller.susserver.dto.LogEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks coverage information, i.e., which line was visited how many times.
 */
// Needs to be public to be callable during test execution.
@Slf4j
public class InstrumentationTracker {

    private static final InstrumentationTracker INSTANCE = new InstrumentationTracker();
    private static final Map<String, ClassTracker> classTrackers = new TreeMap<>();
    private static final Map<String, AtomicInteger> userStepCounters = new ConcurrentHashMap<>();

    private InstrumentationTracker() {
    }

    public static InstrumentationTracker getInstance() {
        return INSTANCE;
    }

    public Map<String, ClassTracker> getClassTrackers() {
        return classTrackers;
    }

    /**
     * Track a visit of a line.
     *
     * @param pLineNumber The line number that was tracked
     * @param pClassName  The class in which the line is
     */
    // Needs to be public to be callable during test execution.
    @SuppressWarnings("unused")
    public static void trackLineVisit(final int pLineNumber, final String pClassName) {
        if (classTrackers.containsKey(pClassName)) {
            classTrackers.get(pClassName).visitLine(pLineNumber);
        } else {
            final ClassTracker classTracker = new ClassTracker();
            classTracker.visitLine(pLineNumber);
            classTrackers.put(pClassName, classTracker);
        }
    }

    /**
     * Track the existence of a line.
     *
     * @param pLineNumber The line number to track
     * @param pClassName  The class in which the line is
     */
    public static void trackLine(final int pLineNumber, final String pClassName) {
        if (classTrackers.containsKey(pClassName)) {
            classTrackers.get(pClassName).trackLine(pLineNumber);
        } else {
            final ClassTracker classTracker = new ClassTracker();
            classTracker.trackLine(pLineNumber);
            classTrackers.put(pClassName, classTracker);
        }
    }

    public static void trackVar(final Object value, final int pVarIndex, final String pClassName, final String methodName) {
        if (classTrackers.containsKey(pClassName)) {
            classTrackers.get(pClassName).trackVariableValueChanged(value, pVarIndex, methodName);
        } else {
            final ClassTracker classTracker = new ClassTracker();
            classTracker.trackVariableValueChanged(value, pVarIndex, methodName);
            classTrackers.put(pClassName, classTracker);
        }
        log.debug("[trackVar] idx={} class={} method={} value={}", pVarIndex, pClassName, methodName, value);
    }
    @SuppressWarnings("unused")
    public static void trackVar(final int value, final int pVarIndex, final String pClassName, final String methodName) {
        trackVar((Integer) value, pVarIndex, pClassName, methodName);
    }
    @SuppressWarnings("unused")
    public static void trackVar(final long value, final int pVarIndex, final String pClassName, final String methodName) {
        trackVar((Long) value, pVarIndex, pClassName, methodName);
    }
    @SuppressWarnings("unused")
    public static void trackVar(final float value, final int pVarIndex, final String pClassName, final String methodName) {
        trackVar((Float) value, pVarIndex, pClassName, methodName);
    }
    @SuppressWarnings("unused")
    public static void trackVar(final double value, final int pVarIndex, final String pClassName, final String methodName) {
        trackVar((Double) value, pVarIndex, pClassName, methodName);
    }

    @SuppressWarnings("unused")
    public static void trackField(final int value, final String fieldName, final String pClassName, final String methodName) {
        classTrackers.computeIfAbsent(pClassName, k -> new ClassTracker()).trackFieldValueChanged(value, fieldName);
    }
    @SuppressWarnings("unused")
    public static void trackField(final long value, final String fieldName, final String pClassName, final String methodName) {
        classTrackers.computeIfAbsent(pClassName, k -> new ClassTracker()).trackFieldValueChanged(value, fieldName);
    }
    @SuppressWarnings("unused")
    public static void trackField(final float value, final String fieldName, final String pClassName, final String methodName) {
        classTrackers.computeIfAbsent(pClassName, k -> new ClassTracker()).trackFieldValueChanged(value, fieldName);
    }
    @SuppressWarnings("unused")
    public static void trackField(final double value, final String fieldName, final String pClassName, final String methodName) {
        classTrackers.computeIfAbsent(pClassName, k -> new ClassTracker()).trackFieldValueChanged(value, fieldName);
    }
    @SuppressWarnings("unused")
    public static void trackField(final Object value, final String fieldName, final String pClassName, final String methodName) {
        classTrackers.computeIfAbsent(pClassName, k -> new ClassTracker()).trackFieldValueChanged(value, fieldName);
    }

    @SuppressWarnings("unused")
    public static void trackFieldBool(final int value, final String fieldName, final String pClassName, final String methodName) {
        classTrackers.computeIfAbsent(pClassName, k -> new ClassTracker()).trackFieldValueChanged(Boolean.toString(value != 0), fieldName);
    }

    public static void trackVarDef(final int pVarIndex, final String pVarName, final String pVarDesc,
                                   final String pClassName, final String methodName) {
        if (classTrackers.containsKey(pClassName)) {
            classTrackers.get(pClassName).trackVariableDefinition(pVarIndex, methodName + "/" + pVarName, pVarDesc, methodName);
        } else {
            final ClassTracker classTracker = new ClassTracker();
            classTracker.trackVariableDefinition(pVarIndex, methodName + "/" + pVarName, pVarDesc, methodName);
            classTrackers.put(pClassName, classTracker);
        }
        log.debug("[trackVarDef] idx={} name={} desc={} class={} method={}", pVarIndex, pVarName, pVarDesc, pClassName, methodName);
    }

    public static void trackLog(String message, String pClassName, String methodName) {
        classTrackers.computeIfAbsent(pClassName, k -> new ClassTracker())
            .trackLog(message, methodName);
    }

    @SuppressWarnings("unused")
    public static void trackDebugStep(final int pLineNumber, final String pClassName, final String methodName) {
        final String userId = pClassName.contains("#")
                ? pClassName.substring(pClassName.lastIndexOf('#') + 1)
                : "";
        final int globalIdx = userStepCounters.computeIfAbsent(userId, k -> new AtomicInteger()).getAndIncrement();
        classTrackers.computeIfAbsent(pClassName, k -> new ClassTracker())
                .captureDebugStep(pLineNumber, methodName, globalIdx);
    }

    @SuppressWarnings("unused")
    public static void trackEnterTestMethod(String pTestClassName, String pTestMethodName, String pCutClassId) {
        classTrackers.computeIfAbsent(pCutClassId, k -> new ClassTracker())
            .trackEnterTestMethod(pTestMethodName);
    }

    public Map<String, Map<Integer, Integer>> getCoverage() {
        return mapMap(classTrackers, (className, classTracker) -> classTracker.getVisitedLines());
    }

    public Map<String, Set<Integer>> getLines() {
        return mapMap(classTrackers, (className, classTracker) -> classTracker.getLines());
    }

    public Map<String, Set<Integer>> getCoveredLines() {
        return mapMap(classTrackers, (className, classTracker) -> classTracker.getVisitedLines().keySet());
    }

    public Map<String, Map<Integer, Map<String, String>>> getVars() {
        return mapMap(classTrackers,
                (className, classTracker) -> mapMap(classTracker.getVars(),
                        (line, vars) -> mapMap(vars,
                                (varName, value) -> Objects.toString(value, "null")
                        )));
    }

    public Map<String, List<LogEntry>> getLogs() {
        return mapMap(classTrackers, (className, classTracker) -> classTracker.getLogs());
    }

    public Map<String, Map<Integer, Integer>> getCoverageForUser(String userId) {
        return filterMap(getCoverage(), (className, lines) -> className.endsWith("#" + userId));
    }

    public Map<String, Set<Integer>> getLinesForUser(String userId) {
        return filterMap(getLines(), (className, lines) -> className.endsWith("#" + userId));
    }

    public Map<String, Set<Integer>> getCoveredLinesForUser(String userId) {
        return filterMap(getCoveredLines(), (className, lines) -> className.endsWith("#" + userId));
    }

    public Map<String, Map<Integer, Map<String, String>>> getVarsForUser(String userId) {
        return filterMap(getVars(), (className, lines) -> className.endsWith("#" + userId));
    }

    public Map<String, List<LogEntry>> getLogsForUser(String userId) {
        return filterMap(getLogs(), (className, lines) -> className.endsWith("#" + userId));
    }

    public Map<String, List<DebugStep>> getDebugTrace() {
        return mapMap(classTrackers, (className, classTracker) -> classTracker.getDebugTrace());
    }

    public Map<String, List<DebugStep>> getDebugTraceForUser(String userId) {
        return filterMap(getDebugTrace(), (className, trace) -> className.endsWith("#" + userId));
    }

    public void clearForUser(String userId) {
        classTrackers.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("#" + userId))
                .forEach(entry -> entry.getValue().clear());
        userStepCounters.computeIfAbsent(userId, k -> new AtomicInteger()).set(0);
    }

    @Getter
    public static class ClassTracker {
        private int lastVisitedLine = 0;
        private int logIndex = 0;
        private int stepIndex = 0;
        private String currentTestMethod = null;
        private final Map<Integer, Integer> visitedLines = new TreeMap<>();
        private final Set<Integer> lines = new HashSet<>();
        private final Map<String, String[]> currentIndexToVarNameAndDescriptor = new TreeMap<>();
        private final Map<Integer, Map<String, Object>> vars = new TreeMap<>();
        private final List<LogEntry> logs = new LinkedList<>();
        private final Map<String, Object> liveVarState = new LinkedHashMap<>();
        private final List<DebugStep> debugTrace = new ArrayList<>();

        void visitLine(final int pLineNumber) {
            if (visitedLines.containsKey(pLineNumber)) {
                visitedLines.merge(pLineNumber, 1, Integer::sum);
            } else {
                visitedLines.put(pLineNumber, 1);
            }
            lastVisitedLine = pLineNumber;
        }

        void trackLine(final int pLineNumber) {
            lines.add(pLineNumber);
        }

        void trackVariableValueChanged(final Object value, final int pVarIndex, final String methodName) {
            String varId = methodName + "/" + pVarIndex;
            if (!currentIndexToVarNameAndDescriptor.containsKey(varId)) {
                log.debug("No var def found for {}", varId);
                return;
            }
            String varName = currentIndexToVarNameAndDescriptor.get(varId)[0];
            log.debug("[trackVar] FOUND varId={} varName={} value={}", varId, varName, value);
            if (vars.containsKey(lastVisitedLine)) {
                vars.get(lastVisitedLine).put(varName, value);
            } else {
                final Map<String, Object> varMap = new TreeMap<>();
                varMap.put(varName, value);
                vars.put(lastVisitedLine, varMap);
            }
            liveVarState.put(varName, value);
        }

        void captureDebugStep(final int lineNumber, final String methodName, final int globalIndex) {
            Map<String, String> snapshot = new LinkedHashMap<>();
            liveVarState.forEach((qualifiedName, value) -> {
                String shortName = qualifiedName.contains("/")
                        ? qualifiedName.substring(qualifiedName.lastIndexOf('/') + 1)
                        : qualifiedName;
                snapshot.put(shortName, formatValue(value));
            });
            log.debug("[captureDebugStep] line={} method={} liveVars={} snapshot={}", lineNumber, methodName, liveVarState.size(), snapshot);
            debugTrace.add(new DebugStep(globalIndex, stepIndex++, lineNumber, methodName, currentTestMethod, snapshot));
        }

        private static String formatValue(Object value) {
            if (value == null) return "null";
            return switch (value.getClass().getName()) {
                case "[I" -> Arrays.toString((int[])     value);
                case "[J" -> Arrays.toString((long[])    value);
                case "[D" -> Arrays.toString((double[])  value);
                case "[F" -> Arrays.toString((float[])   value);
                case "[Z" -> Arrays.toString((boolean[]) value);
                case "[B" -> Arrays.toString((byte[])    value);
                case "[S" -> Arrays.toString((short[])   value);
                case "[C" -> Arrays.toString((char[])    value);
                default   -> value.getClass().isArray()
                             ? Arrays.deepToString((Object[]) value)
                             : Objects.toString(value);
            };
        }

        void trackFieldValueChanged(final Object value, final String fieldName) {
            liveVarState.put(fieldName, value);
        }

        void trackVariableDefinition(final int pVarIndex, final String pVarName, String pVarDesc, final String methodName) {
            String varId = methodName + "/" + pVarIndex;
            currentIndexToVarNameAndDescriptor.put(varId, new String[] {pVarName, pVarDesc});
        }

        void trackLog(String message, String methodName) {
            logs.add(new LogEntry(logIndex++, message, methodName, lastVisitedLine, currentTestMethod));
        }

        void trackEnterTestMethod(final String pMethodName) {
            currentTestMethod = pMethodName;
        }

        public void clear() {
            visitedLines.clear();
            lines.clear();
            currentIndexToVarNameAndDescriptor.clear();
            vars.clear();
            logs.clear();
            liveVarState.clear();
            debugTrace.clear();
            lastVisitedLine = 0;
            logIndex = 0;
            stepIndex = 0;
            currentTestMethod = null;
        }
    }
}
