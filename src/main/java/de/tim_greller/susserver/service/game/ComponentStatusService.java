package de.tim_greller.susserver.service.game;

import java.util.Collection;
import java.util.Optional;

import de.tim_greller.susserver.dto.CutSourceDTO;
import de.tim_greller.susserver.dto.TestExecutionResultDTO;
import de.tim_greller.susserver.dto.TestSourceDTO;
import de.tim_greller.susserver.dto.TestStatus;
import de.tim_greller.susserver.events.ComponentDestroyedEvent;
import de.tim_greller.susserver.events.ComponentTestsActivatedEvent;
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
import de.tim_greller.susserver.persistence.repository.ComponentRepository;
import de.tim_greller.susserver.persistence.repository.ComponentStatusRepository;
import de.tim_greller.susserver.persistence.repository.PatchRepository;
import de.tim_greller.susserver.persistence.repository.UserRepository;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.execution.CutService;
import de.tim_greller.susserver.service.execution.DebugMainService;
import de.tim_greller.susserver.service.execution.ExecutionService;
import de.tim_greller.susserver.service.execution.TestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ComponentStatusService {

    private final EventService eventService;
    private final PatchRepository patchRepository;
    private final ActivePatchRepository activePatchRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final ExecutionService executionService;
    private final TestService testService;
    private final ComponentStatusRepository componentStatusRepository;
    private final ComponentRepository componentRepository;
    private final CutService cutService;
    private final DebugMainService debugMainService;


    @Transactional
    public ComponentStatusEntity getComponentStatus(String componentName, String userId) {
        return componentStatusRepository.findByKey(componentName, userId)
                .orElseGet(() -> {
                            ComponentStatusEntity e = ComponentStatusEntity.builder()
                                    .userComponentKey(new UserComponentKey(
                                            componentRepository.findById(componentName).orElseThrow(),
                                            userRepository.findById(userId).orElseThrow()))
                                    .stage(1)
                                    .testsActivated(false)
                                    .build();
                            try {
                                return componentStatusRepository.save(e);
                            } catch (DataIntegrityViolationException ignored) {
                                // catch race condition causing 2 objects in detached state to be saved at the same time,
                                // leading to a duplicate key constraint violation.
                                return componentStatusRepository.findByKey(componentName, userId).orElseThrow();
                            }
                        }
                );
    }

    /**
     * Handles the ComponentTestsActivatedEvent by setting the testsActivated flag in the component status.
     *
     * @param event The event to handle.
     * @return true if the testsActivated flag was changed, false if it was already active before.
     */
    @Transactional
    boolean handleComponentTestsActivated(ComponentTestsActivatedEvent event) {
        final var componentName = event.getComponentName();
        log.info("Component tests activated for component {}", componentName);
        final var componentStatus = getComponentStatus(componentName, userService.requireCurrentUserId());
        if (componentStatus.isTestsActivated()) {
            log.info("Component tests already activated for component {}", componentName);
            return false;
        }
        // TODO: server side validation: check for no test failures
        componentStatus.setTestsActivated(true);
        componentStatusRepository.save(componentStatus);
        return true;
    }

    /**
     * Selects and apply a mutation (=patch) to the component.
     */
    void attackCut(final String componentName) {
        log.info("Component {} is being attacked", componentName);

        final int stage = getComponentStatus(componentName, userService.requireCurrentUserId()).getStage();
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
            final ComponentStatusEntity componentStatus = getComponentStatus(componentName, userId);
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

    // TODO: Long term this method should only be called on new game creation.
    // Only resets the given components so resetting one strand keeps the other strand's progress.
    @Transactional
    public void resetComponentStatus(String userId, Collection<String> componentNames) {
        componentStatusRepository.deleteAllByUserAndComponents(userId, componentNames);
        activePatchRepository.deleteAllByUserAndComponents(userId, componentNames);
        testService.resetTestsForUser(userId, componentNames);
        debugMainService.resetRunnersForUser(userId, componentNames);
        cutService.resetCutsForUser(userId, componentNames);
    }
}
