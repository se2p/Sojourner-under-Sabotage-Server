package de.tim_greller.susserver.service.game;

import java.util.Collection;

import de.tim_greller.susserver.events.ComponentTestsActivatedEvent;
import de.tim_greller.susserver.persistence.entity.ComponentStatusEntity;
import de.tim_greller.susserver.persistence.keys.UserComponentKey;
import de.tim_greller.susserver.persistence.repository.ComponentRepository;
import de.tim_greller.susserver.persistence.repository.ComponentStatusRepository;
import de.tim_greller.susserver.persistence.repository.UserRepository;
import de.tim_greller.susserver.service.auth.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store for the per-user component status (stage, testsActivated).
 * Depends on no other domain service so the execution services can inject it.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComponentStatusService {

    private final ComponentStatusRepository componentStatusRepository;
    private final ComponentRepository componentRepository;
    private final UserRepository userRepository;
    private final UserService userService;


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

    // Read-only stage lookup
    public int getStage(String componentName, String userId) {
        return componentStatusRepository.findByKey(componentName, userId)
                .map(ComponentStatusEntity::getStage)
                .orElse(1);
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

    // Only deletes the status rows themselves; the mode-scoped reset across all
    // per-user component data is orchestrated by GameProgressionService.
    @Transactional
    public void resetComponentStatus(String userId, Collection<String> componentNames) {
        componentStatusRepository.deleteAllByUserAndComponents(userId, componentNames);
    }
}