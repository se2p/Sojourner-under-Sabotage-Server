package de.tim_greller.susserver.controller.api;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.dto.GameProgressStatus;
import de.tim_greller.susserver.dto.PlainSource;
import de.tim_greller.susserver.dto.TestExecutionResultDTO;
import de.tim_greller.susserver.exception.ClassLoadException;
import de.tim_greller.susserver.exception.CompilationException;
import de.tim_greller.susserver.exception.NotFoundException;
import de.tim_greller.susserver.exception.TestExecutionException;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.execution.ExecutionService;
import de.tim_greller.susserver.service.execution.TestService;
import de.tim_greller.susserver.service.game.ActiveGameModeService;
import de.tim_greller.susserver.service.game.GameProgressionService;
import de.tim_greller.susserver.service.tracking.UserEventTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class TestExecutionController {

    private final TestService testService;
    private final UserService userService;
    private final ExecutionService executionService;
    private final UserEventTrackingService trackingService;
    private final ActiveGameModeService activeModeService;
    private final GameProgressionService gameProgressionService;


    @PostMapping(value = "${paths.api}/components/{componentName}/test/execute")
    public @ResponseBody TestExecutionResultDTO execute(@PathVariable String componentName,
                                                        @RequestBody PlainSource testSource) {
        // save the current source code
        testService.updateTestForComponent(
                componentName,
                userService.requireCurrentUserId(),
                testSource.getCode()
        );

        activeModeService.bindMode(GameMode.Testing);

        // compile and execute
        try {
            // In the testing strand's DEBUGGING phase a green run is verified against the hidden tests
            boolean verifyFix = gameProgressionService.getCurrentGameProgression()
                    .map(p -> p.getStatus() == GameProgressStatus.DEBUGGING)
                    .orElse(false);
            var result = executionService.execute(componentName, userService.requireCurrentUserId(), verifyFix);
            trackingService.trackEvent("test-executed", result);
            return result;
        } catch (CompilationException | ClassLoadException | TestExecutionException e) {
            trackingService.trackEvent("test-execution-failed", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (NotFoundException e) {
            trackingService.trackEvent("test-not-found", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } finally {
            activeModeService.clearMode();
        }
    }
}
