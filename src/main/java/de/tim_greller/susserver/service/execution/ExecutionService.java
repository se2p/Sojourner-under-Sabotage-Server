package de.tim_greller.susserver.service.execution;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static de.tim_greller.susserver.dto.TestStatus.FAILED;
import static de.tim_greller.susserver.dto.TestStatus.PASSED;
import static de.tim_greller.susserver.util.Utils.mapMap;

import de.tim_greller.susserver.dto.GameProgressStatus;
import de.tim_greller.susserver.dto.TestDetailsDTO;
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
import de.tim_greller.susserver.persistence.keys.UserKey;
import de.tim_greller.susserver.persistence.repository.ComponentStatusRepository;
import de.tim_greller.susserver.persistence.repository.UserGameProgressionRepository;
import de.tim_greller.susserver.service.auth.UserService;
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
    private final CutService cutService;
    private final TestService testService;
    private final UserService userService;
    private final ComponentStatusRepository componentStatusRepository;
    private final EventService eventService;
    private final UserGameProgressionRepository userGameProgressionRepository;
    @Value("${jarsToInclude}") private List<String> jarsToInclude;


    //temp
    public TestExecutionResultDTO executeDebugRunner(String componentName, String userId, String runnerCode)
            throws ClassLoadException, NotFoundException, TestExecutionException, CompilationException {
        final var iTracker = InstrumentationTracker.getInstance();
        iTracker.clearForUser(userId);
        final var clientResultDto = new TestExecutionResultDTO();

        var runnerSource = new TestSourceDTO(componentName, "DebugRunner", runnerCode, List.of());
        final Class<?> testClass = compile(runnerSource, componentName, userId);
        final var listener = new TestRunListener();
        final TestExecutionResult r = run(testClass, listener);

        clientResultDto.setTestClassName("DebugRunner");
        clientResultDto.setTestStatus(r.getStatus());
        clientResultDto.setTestDetails(listener.getMap());
        clientResultDto.setElapsedTime(listener.getTestSuiteElapsedTime());
        clientResultDto.setCoverage(iTracker.getCoverageForUser(userId));
        clientResultDto.setVariables(iTracker.getVarsForUser(userId));
        clientResultDto.setLogs(iTracker.getLogsForUser(userId));
        clientResultDto.setDebugTrace(iTracker.getDebugTraceForUser(userId));
        clientResultDto.setCoveredLines(mapMap(iTracker.getCoveredLinesForUser(userId), (k, v) -> v.size()));
        clientResultDto.setTotalLines(mapMap(iTracker.getLinesForUser(userId), (k, v) -> v.size()));
        return clientResultDto;
    }

    public TestExecutionResultDTO execute(String componentName, String userId)
            throws ClassLoadException, NotFoundException, TestExecutionException, CompilationException {
        final var iTracker = InstrumentationTracker.getInstance();
        iTracker.clearForUser(userId);
        final var clientResultDto = new TestExecutionResultDTO();
        final Class<?> testClass = compile(componentName, userId);
        final var listener = new TestRunListener();
        final TestExecutionResult r = run(testClass, listener);

        boolean isDebugging = userGameProgressionRepository.findById(new UserKey(userService.requireCurrentUser())).orElseThrow().getStatus() == GameProgressStatus.DEBUGGING;
        if (r.wasSuccessful() && isDebugging) { // tests passed while in debug mode: check if really fixed
            Class<?> fallbackTestClass = compileFallbackTests(componentName, userId);
            var fallbackListener = new TestRunListener();
            TestExecutionResult fallbackResult = run(fallbackTestClass, fallbackListener);
            if (fallbackResult.wasSuccessful()) {
                clientResultDto.setHiddenTestsPassed(true);
                eventService.publishAndHandleEvent(new ComponentFixedEvent(componentName));
            } else {
                clientResultDto.setHiddenTestsPassed(false);
                // TODO: add all failing methods or only one?
                for (Map.Entry<String, TestDetailsDTO> entry : fallbackListener.getMap().entrySet()) {
                    String methodName = entry.getKey();
                    TestDetailsDTO testDetails = entry.getValue();
                    if (testDetails.getTestStatus() == FAILED) {
                        testService.addHiddenTestMethodToUserTest(methodName, componentName, userId);
                        eventService.publishEvent(new ComponentTestsExtendedEvent(componentName, methodName));
                        // execute tests again.
                        // (Now the user tests will fail, so the hidden tests aren't executed again)
                        return execute(componentName, userId);
                    }
                }
            }
        }

        // OutputWriter.writeShellOutput(iTracker.getClassTrackers());

        clientResultDto.setTestClassName(testClass.getName());
        clientResultDto.setTestStatus(r.getStatus());
        clientResultDto.setTestDetails(listener.getMap());
        clientResultDto.setElapsedTime(listener.getTestSuiteElapsedTime());
        clientResultDto.setCoverage(iTracker.getCoverageForUser(userId));
        clientResultDto.setVariables(iTracker.getVarsForUser(userId));
        clientResultDto.setLogs(iTracker.getLogsForUser(userId));
        clientResultDto.setDebugTrace(iTracker.getDebugTraceForUser(userId));
        clientResultDto.setCoveredLines(mapMap(iTracker.getCoveredLinesForUser(userId), (k, v) -> v.size()));
        clientResultDto.setTotalLines(mapMap(iTracker.getLinesForUser(userId), (k, v) -> v.size()));
        return clientResultDto;
    }

    public TestExecutionResultDTO addFailingHiddenTest(String componentName, String userId)
            throws TestExecutionException, CompilationException, ClassLoadException, NotFoundException {
        Class<?> fallbackTestClass = compileFallbackTests(componentName, userId);
        var fallbackListener = new TestRunListener();
        TestExecutionResult fallbackResult = run(fallbackTestClass, fallbackListener);
        if (fallbackResult.wasSuccessful()) {
            throw new IllegalStateException("Hidden tests passed, so no failing test can be added.");
        } else {
            for (Map.Entry<String, TestDetailsDTO> entry : fallbackListener.getMap().entrySet()) {
                String methodName = entry.getKey();
                TestDetailsDTO testDetails = entry.getValue();
                if (testDetails.getTestStatus() == FAILED) {
                    testService.addHiddenTestMethodToUserTest(methodName, componentName, userId);
                    eventService.publishEvent(new ComponentTestsExtendedEvent(componentName, methodName));
                    // execute tests again.
                    // (the user tests will fail, so the hidden tests aren't executed again)
                    return execute(componentName, userId);
                }
            }
        }

        throw new IllegalStateException("No failing test found in hidden tests.");
    }

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
        // Cannot inject componentStatusService due to circular dependency. But the component should always have a status.
        var componentStatus = componentStatusRepository.findByKey(componentName, userId).orElseThrow();
        var testSource = testService.getHiddenTestForComponent(componentStatus);
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
