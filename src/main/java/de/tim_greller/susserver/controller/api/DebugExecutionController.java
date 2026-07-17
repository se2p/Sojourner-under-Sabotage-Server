package de.tim_greller.susserver.controller.api;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.dto.PlainSource;
import de.tim_greller.susserver.dto.TestExecutionResultDTO;
import de.tim_greller.susserver.exception.ClassLoadException;
import de.tim_greller.susserver.exception.CompilationException;
import de.tim_greller.susserver.exception.NotFoundException;
import de.tim_greller.susserver.exception.TestExecutionException;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.execution.DebugMainService;
import de.tim_greller.susserver.service.execution.ExecutionService;
import de.tim_greller.susserver.service.game.ActiveGameModeService;
import de.tim_greller.susserver.service.tracking.UserEventTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class DebugExecutionController {

    private final UserService userService;
    private final ExecutionService executionService;
    private final DebugMainService debugMainService;
    private final ActiveGameModeService activeModeService;
    private final UserEventTrackingService trackingService;

    // Save and run the user's runner body (the server wraps it into the DebugRunner class)
    // and return the result including the debug trace.
    @PostMapping(value = "${paths.api}/components/{componentName}/debug/execute")
    public @ResponseBody TestExecutionResultDTO executeDebugRunner(
            @PathVariable String componentName,
            @RequestBody PlainSource runnerSource) {
        debugMainService.saveRunnerForUser(componentName, userService.requireCurrentUserId(), runnerSource.getCode());

        activeModeService.bindMode(GameMode.Debugging);
        try {
            var result = executionService.executeDebugRunner(
                    componentName,
                    userService.requireCurrentUserId(),
                    runnerSource.getCode()
            );
            trackingService.trackEvent("debug-executed", result);
            return result;
        } catch (CompilationException | ClassLoadException | TestExecutionException e) {
            trackingService.trackEvent("debug-execution-failed", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (NotFoundException e) {
            trackingService.trackEvent("debug-not-found", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } finally {
            activeModeService.clearMode();
        }
    }

    // Return the debug runner for a component: the user's saved edits if present,
    // otherwise the shared template for the user's current stage.
    @GetMapping(value = "${paths.api}/components/{componentName}/debug/main")
    public @ResponseBody PlainSource getDebugMain(@PathVariable String componentName) {
        String sourceCode = debugMainService
                .getRunnerForUser(componentName, userService.requireCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No debug main found for " + componentName));
        PlainSource result = new PlainSource();
        result.setCode(sourceCode);
        return result;
    }

    // Persist the user's runner edits for a component.
    @PutMapping(value = "${paths.api}/components/{componentName}/debug/main")
    public void updateDebugMain(
            @PathVariable String componentName,
            @RequestBody PlainSource newSource) {
        debugMainService.saveRunnerForUser(
                componentName,
                userService.requireCurrentUserId(),
                newSource.getCode()
        );
    }
}