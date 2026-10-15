package de.tim_greller.susserver.service.execution;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static de.tim_greller.susserver.dto.TestStatus.FAILED;
import static de.tim_greller.susserver.dto.TestStatus.PASSED;
import static de.tim_greller.susserver.util.Utils.mapMap;

import de.tim_greller.susserver.dto.TestExecutionResultDTO;
import de.tim_greller.susserver.dto.TestSourceDTO;
import de.tim_greller.susserver.dto.TestStatus;
import de.tim_greller.susserver.events.ComponentFixedEvent;
import de.tim_greller.susserver.events.ComponentTestsExtendedEvent;
import de.tim_greller.susserver.exception.ClassLoadException;
import de.tim_greller.susserver.exception.CompilationException;
import de.tim_greller.susserver.exception.NotFoundException;
import de.tim_greller.susserver.exception.TestExecutionException;
import de.tim_greller.susserver.exception.TestExecutionTimedOut;
import de.tim_greller.susserver.model.execution.compilation.InMemoryCompiler;
import de.tim_greller.susserver.model.execution.instrumentation.InstrumentationTracker;
import de.tim_greller.susserver.model.execution.instrumentation.TestRunListener;
import de.tim_greller.susserver.model.execution.instrumentation.transformer.CoverageClassTransformer;
import de.tim_greller.susserver.model.execution.instrumentation.transformer.TestClassTransformer;
import de.tim_greller.susserver.service.game.ComponentStatusService;
import de.tim_greller.susserver.service.game.EventService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private static final int MAX_TEST_EXECUTION_TIME_SECONDS = 1;
    
    private static final String RUNNER_CLASS_NAME = "DebugRunner";
    private static final String RUNNER_HEADER = "import java.util.*;\n"
            + "\n"
            + "public class " + RUNNER_CLASS_NAME + " {\n"
            + "\n"
            + "    @org.junit.jupiter.api.Test\n"
            + "    void run() throws Exception {\n";
    private static final String RUNNER_FOOTER = "    }\n}\n";
    private static final int RUNNER_LINE_OFFSET = (int) RUNNER_HEADER.lines().count();

    private final CutService cutService;
    private final TestService testService;
    private final ComponentStatusService componentStatusService;
    private final EventService eventService;
    @Value("${jarsToInclude}") private List<String> jarsToInclude;


    // Wrap the runner body, compile and run it, then run the hidden tests to report whether the fix works.
    // The trailing newline before the footer prevents a body ending in a // comment from swallowing the brace.
    public TestExecutionResultDTO executeDebugRunner(String componentName, String userId, String runnerBody)
            throws ClassLoadException, NotFoundException, TestExecutionException, CompilationException {
        InstrumentationTracker.getInstance().clearForUser(userId);

        var runnerCode = RUNNER_HEADER + runnerBody + "\n" + RUNNER_FOOTER;
        var runnerSource = new TestSourceDTO(componentName, RUNNER_CLASS_NAME, runnerCode, List.of());
        final Class<?> testClass = compile(runnerSource, componentName, userId);
        final var listener = new TestRunListener();
        final TestExecutionResult r = run(testClass, listener);

        final var clientResultDto = new TestExecutionResultDTO();
        clientResultDto.setRunnerLineOffset(RUNNER_LINE_OFFSET);
        populateResult(clientResultDto, testClass.getName(), r, listener, userId);

        // Skip the second compile + hidden-suite run while the runner itself fails.
        if (r.wasSuccessful()) {
            verifyDebugFix(componentName, userId, clientResultDto);
        } else {
            clientResultDto.setHiddenTestsError("The hidden tests only run once your code executes without errors.");
        }
        return clientResultDto;
    }

    // Run the hidden fallback tests and report whether they pass. A passing run publishes a
    // ComponentFixedEvent; whether that advances the game is decided by the handler's guard
    // (no-op outside the DEBUGGING phase, e.g. for runs on the standalone debug page).
    // Player-caused failures (broken interface, endless loop) are reported via hiddenTestsError;
    // infrastructure errors and failures of the progression handler propagate to the controller.
    private void verifyDebugFix(String componentName, String userId, TestExecutionResultDTO clientResultDto)
            throws NotFoundException, ClassLoadException, TestExecutionException {
        boolean passed;
        try {
            var fallback = runHiddenTests(componentName, userId);
            passed = fallback.result().wasSuccessful();
            if (!passed) {
                logFailingHiddenTests(componentName, fallback.listener());
            }
        } catch (CompilationException e) {
            log.info("Hidden tests for component {} no longer compile: {}", componentName, e.getMessage());
            clientResultDto.setHiddenTestsPassed(false);
            clientResultDto.setHiddenTestsError("The hidden tests don't compile against your version of the class."
                    + " Keep its public methods and signatures unchanged.\n" + e.getMessage());
            return;
        } catch (TestExecutionTimedOut e) {
            log.info("Hidden tests for component {} timed out.", componentName);
            clientResultDto.setHiddenTestsPassed(false);
            clientResultDto.setHiddenTestsError("The hidden tests timed out — check your fix for endless loops. ("
                    + e.getMessage() + ")");
            return;
        }
        clientResultDto.setHiddenTestsPassed(passed);
        if (passed) {
            eventService.publishAndHandleEvent(new ComponentFixedEvent(componentName));
        }
    }

    // Server-side only: the player must not learn which hidden test failed, but without this the
    // verdict is a bare boolean and a failing fix can't be diagnosed.
    private void logFailingHiddenTests(String componentName, TestRunListener listener) {
        listener.getMap().forEach((methodName, details) -> {
            if (details.getTestStatus() != FAILED) {
                return;
            }
            // expected/actual are only set for assertion failures; anything else (e.g. a SecurityException
            // from the sandbox) carries its information in the trace alone.
            var assertion = details.getExpectedTestResult() == null
                    ? "no assertion failure"
                    : "expected <" + details.getExpectedTestResult() + "> but was <"
                            + details.getActualTestResult() + ">";
            log.info("Hidden test {}.{} failed ({}):\n{}",
                    componentName, methodName, assertion, details.getTrace());
        });
    }

    public TestExecutionResultDTO execute(String componentName, String userId)
            throws ClassLoadException, NotFoundException, TestExecutionException, CompilationException {
        return execute(componentName, userId, false);
    }
    
    public TestExecutionResultDTO execute(String componentName, String userId, boolean verifyFix)
            throws ClassLoadException, NotFoundException, TestExecutionException, CompilationException {
        InstrumentationTracker.getInstance().clearForUser(userId);
        final var clientResultDto = new TestExecutionResultDTO();
        final Class<?> testClass = compile(componentName, userId);
        final var listener = new TestRunListener();
        final TestExecutionResult r = run(testClass, listener);

        // Snapshot the user's run before the hidden tests write into the same trackers.
        populateResult(clientResultDto, testClass.getName(), r, listener, userId);

        if (r.wasSuccessful() && verifyFix) {
            var fallback = runHiddenTests(componentName, userId);
            if (fallback.result().wasSuccessful()) {
                clientResultDto.setHiddenTestsPassed(true);
                eventService.publishAndHandleEvent(new ComponentFixedEvent(componentName));
            } else {
                clientResultDto.setHiddenTestsPassed(false);
                var reexecuted = addFirstFailingHiddenTestAndReexecute(fallback.listener(), componentName, userId);
                if (reexecuted != null) {
                    return reexecuted;
                }
            }
        }

        return clientResultDto;
    }

    public TestExecutionResultDTO addFailingHiddenTest(String componentName, String userId)
            throws TestExecutionException, CompilationException, ClassLoadException, NotFoundException {
        var fallback = runHiddenTests(componentName, userId);
        if (fallback.result().wasSuccessful()) {
            throw new IllegalStateException("Hidden tests passed, so no failing test can be added.");
        }
        var reexecuted = addFirstFailingHiddenTestAndReexecute(fallback.listener(), componentName, userId);
        if (reexecuted != null) {
            return reexecuted;
        }
        throw new IllegalStateException("No failing test found in hidden tests.");
    }
    
    private FallbackRun runHiddenTests(String componentName, String userId)
            throws NotFoundException, ClassLoadException, TestExecutionException, CompilationException {
        Class<?> fallbackTestClass = compileFallbackTests(componentName, userId);
        var listener = new TestRunListener();
        var result = run(fallbackTestClass, listener);
        return new FallbackRun(result, listener);
    }
    
    private TestExecutionResultDTO addFirstFailingHiddenTestAndReexecute(
            TestRunListener fallbackListener, String componentName, String userId)
            throws ClassLoadException, NotFoundException, TestExecutionException, CompilationException {
        for (var entry : fallbackListener.getMap().entrySet()) {
            if (entry.getValue().getTestStatus() == FAILED) {
                testService.addHiddenTestMethodToUserTest(entry.getKey(), componentName, userId);
                eventService.publishEvent(new ComponentTestsExtendedEvent(componentName, entry.getKey()));
                // re-run: the user tests now fail, so the hidden tests aren't executed again
                return execute(componentName, userId);
            }
        }
        return null;
    }
    
    private void populateResult(TestExecutionResultDTO dto, String testClassName,
                                TestExecutionResult result, TestRunListener listener, String userId) {
        final var iTracker = InstrumentationTracker.getInstance();
        dto.setTestClassName(testClassName);
        dto.setTestStatus(result.getStatus());
        dto.setTestDetails(listener.getMap());
        dto.setElapsedTime(listener.getTestSuiteElapsedTime());
        dto.setCoverage(iTracker.getCoverageForUser(userId));
        dto.setVariables(iTracker.getVarsForUser(userId));
        dto.setLogs(iTracker.getLogsForUser(userId));
        dto.setDebugTrace(iTracker.getDebugTraceForUser(userId));
        dto.setCoveredLines(mapMap(iTracker.getCoveredLinesForUser(userId), (k, v) -> v.size()));
        dto.setTotalLines(mapMap(iTracker.getLinesForUser(userId), (k, v) -> v.size()));
        dto.setExecutableLines(iTracker.getLinesForUser(userId));
    }
    
    private record FallbackRun(TestExecutionResult result, TestRunListener listener) {}

    /**
     * Fetches the CUT and the test class of a user for the specified component from the database and compiles them.
     *
     * @param componentName The name of the component to compile the classes for.
     * @param userId The id of the user whose classes should be fetched.
     * @return The compiled test class.
     * @throws NotFoundException If the CUT was not found. (If the test is not found an empty one will be created.)
     * @throws ClassLoadException If the test class could not be loaded / was not successfully compiled.
     */
    private Class<?> compile(String componentName, String userId)
            throws NotFoundException, ClassLoadException, CompilationException {
        var testSource = testService.getOrCreateTestDtoForComponent(componentName, userId);
        return compile(testSource, componentName, userId);
    }

    private Class<?> compileFallbackTests(String componentName, String userId)
            throws NotFoundException, ClassLoadException, CompilationException {
        int stage = componentStatusService.getStage(componentName, userId);
        var testSource = testService.getHiddenTestForComponent(componentName, stage);
        return compile(testSource, componentName, userId);
    }

    private Class<?> compile(TestSourceDTO testSource, String componentName, String userId)
            throws NotFoundException, CompilationException, ClassLoadException {
        var compiler = new InMemoryCompiler(userId, jarsToInclude);
        var cutSource = cutService
                .getCurrentCutForComponent(componentName)
                .orElseThrow(() -> new NotFoundException("CUT for the specified component was not found"));

        compiler.addSource(cutSource);
        compiler.addSource(testSource);
        compiler.addTransformer(new CoverageClassTransformer(), cutSource.getClassName());
        compiler.addTransformer(new TestClassTransformer(cutSource.getClassName()), testSource.getClassName());
        compiler.compile();

        return compiler.getClass(testSource.getClassName())
                .orElseThrow(() -> new ClassLoadException(
                        "Error loading the test class \"" + testSource.getClassName() + "\"."
                ));
    }

    private TestExecutionResult run(Class<?> testClass, TestRunListener listener) throws TestExecutionException {
        var launcher = LauncherFactory.create();
        var executionThread = new ExecutionThread(launcher, testClass, listener);
        var timer = new Timer();
        var timeOutTask = new TimeOutTask(executionThread, timer);
        timer.schedule(timeOutTask, MAX_TEST_EXECUTION_TIME_SECONDS * 1000);
        executionThread.start();

        try {
            // wait for the test execution to finish
            executionThread.join();
        } catch (InterruptedException e) {
            throw new TestExecutionException("Error while executing the test", e);
        } finally {
            timer.cancel();
        }

        if (timeOutTask.isThreadTimedOut()) {
            throw new TestExecutionTimedOut(MAX_TEST_EXECUTION_TIME_SECONDS);
        }

        return executionThread.getResult();
    }

    @RequiredArgsConstructor
    private static class ExecutionThread extends Thread {
        @Getter
        private TestExecutionResult result;
        private final Launcher launcher;
        private final Class<?> testClass;
        private final TestRunListener listener;
        
        @Override
        public void run() {
            // The custom class loader provided by the InMemoryCompiler
            var testClassLoader = testClass.getClassLoader();
            // Save the current thread's context class loader
            var originalClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                // Set the custom class loader as the context class loader for JUnit 5
                Thread.currentThread().setContextClassLoader(testClassLoader);

                // Create discovery request with the test class
                var request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClass(testClass))
                    .build();
                
                launcher.registerTestExecutionListeners(listener);
                launcher.execute(request);
                
                // Create a simple result indicating success
                var allPass = listener.getMap().values().stream()
                    .noneMatch(test -> test.getTestStatus() == FAILED);
                result = new TestExecutionResult(allPass);
            } finally {
                // Restore original class loader
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    // Simple result class to replace JUnit 4's Result
    @RequiredArgsConstructor
    @Getter
    private static class TestExecutionResult {
        private final boolean wasSuccessful;
        
        public boolean wasSuccessful() {
            return wasSuccessful;
        }

        public TestStatus getStatus() {
            return wasSuccessful ? PASSED : FAILED;
        }
    }

    @RequiredArgsConstructor
    private static class TimeOutTask extends TimerTask {
        private final Thread thread;
        private final Timer timer;
        @Getter private boolean threadTimedOut = false;

        @Override
        public void run() {
            if (thread != null && thread.isAlive()) {
                //noinspection deprecation
                thread.stop();
                timer.cancel();
                threadTimedOut = true;
            }
        }
    }
}
