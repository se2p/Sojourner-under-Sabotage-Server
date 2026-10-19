package de.tim_greller.susserver.service.game;

import java.util.Collection;
import java.util.Optional;

import de.tim_greller.susserver.dto.CutSourceDTO;
import de.tim_greller.susserver.dto.TestExecutionResultDTO;
import de.tim_greller.susserver.dto.TestSourceDTO;
import de.tim_greller.susserver.dto.TestStatus;
import de.tim_greller.susserver.events.ComponentDestroyedEvent;
import de.tim_greller.susserver.events.Event;
import de.tim_greller.susserver.events.MutatedComponentTestsFailedEvent;
import de.tim_greller.susserver.exception.ClassLoadException;
import de.tim_greller.susserver.exception.CompilationException;
import de.tim_greller.susserver.exception.NotFoundException;
import de.tim_greller.susserver.exception.TestExecutionException;
import de.tim_greller.susserver.persistence.entity.ActivePatchEntity;
import de.tim_greller.susserver.persistence.entity.ComponentStatusEntity;
import de.tim_greller.susserver.persistence.entity.PatchEntity;
import de.tim_greller.susserver.persistence.keys.UserComponentKey;
import de.tim_greller.susserver.persistence.repository.ActivePatchRepository;
import de.tim_greller.susserver.persistence.repository.PatchRepository;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.execution.CutService;
import de.tim_greller.susserver.service.execution.ExecutionService;
import de.tim_greller.susserver.service.execution.TestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sabotage orchestration for the testing strand: applies a mutation (=patch) to a component
 * and publishes the resulting destroyed/mutated event based on the user's test run.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AttackService {

    private final EventService eventService;
    private final PatchRepository patchRepository;
    private final ActivePatchRepository activePatchRepository;
    private final UserService userService;
    private final ExecutionService executionService;
    private final TestService testService;
    private final CutService cutService;
    private final ComponentStatusService componentStatusService;

    /**
     * Selects and apply a mutation (=patch) to the component.
     */
    void attackCut(final String componentName) {
        log.info("Component {} is being attacked", componentName);

        final int stage = componentStatusService
                .getComponentStatus(componentName, userService.requireCurrentUserId())
                .getStage();
        final Optional<PatchEntity> patchOpt =
                patchRepository.findPatchEntitiesByComponentKey_ComponentNameAndComponentKey_Stage(
                        componentName, stage);

        if (patchOpt.isEmpty()) {
            log.info("No patches found for component {}", componentName);
            return;
        }

        final PatchEntity patch = patchOpt.get();

        UserComponentKey key = new UserComponentKey(
                patch.getComponentKey().getComponent(),
                userService.requireCurrentUser()
        );
        activePatchRepository.save(new ActivePatchEntity(key, patch));

        // remove user modification as it would overrule the attack mutation
        cutService.removeUserModification(componentName);

        // execute mutated component
        final TestExecutionResultDTO res = executeTests(componentName);
        if (res == null) {
            return;
        }

        Event e = testExecutionResultToEvent(res, componentName);
        eventService.publishAndHandleEvent(e);
    }

    // Removes the active mutations of the given components, e.g. when their strand is reset.
    void resetAttacks(String userId, Collection<String> componentNames) {
        activePatchRepository.deleteAllByUserAndComponents(userId, componentNames);
    }

    private TestExecutionResultDTO executeTests(final String componentName) {
        try {
            return executionService.execute(componentName, userService.requireCurrentUserId());
        } catch (CompilationException | ClassLoadException | TestExecutionException e) {
            log.error("Failed to execute tests for component {}", componentName, e);
            return null;
        } catch (NotFoundException e) {
            log.error("No CUT found for component {}", componentName, e);
            return null;
        }
    }

    private Event testExecutionResultToEvent(final TestExecutionResultDTO res, final String componentName) {
        final String userId = userService.requireCurrentUserId();
        final CutSourceDTO cut = cutService.getCurrentCutForComponent(componentName).orElseThrow();
        if (res.getTestStatus() == TestStatus.FAILED) {
            log.info("Component {} tests failed", componentName);
            return MutatedComponentTestsFailedEvent.builder()
                    .componentName(componentName)
                    .executionResult(res)
                    .cutSource(cut)
                    .testSource(testService.getOrCreateTestDtoForComponent(componentName, userId))
                    .build();
        } else {
            log.info("Component {} tests passed", componentName);
            final ComponentStatusEntity componentStatus =
                    componentStatusService.getComponentStatus(componentName, userId);
            TestExecutionResultDTO res2;
            try {
                res2 = executionService.addFailingHiddenTest(componentName, userId);
            } catch (TestExecutionException | CompilationException | ClassLoadException | NotFoundException | IllegalStateException e) {
                log.error("Failed to execute tests for component {}", componentName, e);
                // fallback to only autogenerated tests & try again
                testService.replaceWithAutoGeneratedTest(componentStatus, userId);
                res2 = executeTests(componentName);
            }
            TestSourceDTO test = testService.getOrCreateTestDtoForComponent(componentName, userId);
            return ComponentDestroyedEvent.builder()
                    .componentName(componentName)
                    .executionResult(res2)
                    .cutSource(cut)
                    .autoGeneratedTestSource(test)
                    .build();
        }
    }
}