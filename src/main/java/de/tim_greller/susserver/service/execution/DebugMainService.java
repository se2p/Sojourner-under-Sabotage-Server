package de.tim_greller.susserver.service.execution;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import de.tim_greller.susserver.persistence.entity.ComponentEntity;
import de.tim_greller.susserver.persistence.entity.DebugMainEntity;
import de.tim_greller.susserver.persistence.entity.UserDebugMainEntity;
import de.tim_greller.susserver.persistence.entity.UserEntity;
import de.tim_greller.susserver.persistence.keys.UserComponentKey;
import de.tim_greller.susserver.persistence.repository.ComponentRepository;
import de.tim_greller.susserver.persistence.repository.DebugMainRepository;
import de.tim_greller.susserver.persistence.repository.UserDebugMainRepository;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.game.ComponentStatusService;
import de.tim_greller.susserver.service.tracking.UserEventTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DebugMainService {

    private final DebugMainRepository debugMainRepository;
    private final UserDebugMainRepository userDebugMainRepository;
    private final ComponentRepository componentRepository;
    private final ComponentStatusService componentStatusService;
    private final UserService userService;
    private final UserEventTrackingService trackingService;

    // Return the runner the user should see: their saved edits if present,
    // otherwise the shared template for the component and the user's current stage.
    public Optional<String> getRunnerForUser(String componentName, String userId) {
        Optional<String> userRunner = userDebugMainRepository.findByKey(componentName, userId)
                .map(UserDebugMainEntity::getSourceCode);
        if (userRunner.isPresent()) {
            return userRunner;
        }
        int stage = componentStatusService.getStage(componentName, userId);
        return debugMainRepository.findByKey(componentName, stage)
                .map(DebugMainEntity::getSourceCode);
    }

    // Persist the user's runner edits for a component.
    public void saveRunnerForUser(String componentName, String userId, String sourceCode) {
        final Optional<UserDebugMainEntity> existing = userDebugMainRepository.findByKey(componentName, userId);
        // Track only real changes (new runner or edited source), not the no-op auto-saves
        // that fire on every run/editor close.
        final boolean changed = existing.map(e -> !sourceCode.equals(e.getSourceCode())).orElse(true);
        final UserDebugMainEntity entity = existing.orElseGet(() -> {
            final ComponentEntity component = componentRepository.findById(componentName).orElseThrow();
            final UserEntity user = userService.loadUserByUsername(userId).orElseThrow();
            return new UserDebugMainEntity(new UserComponentKey(component, user), sourceCode);
        });
        entity.setSourceCode(sourceCode);
        userDebugMainRepository.save(entity);
        if (changed) {
            // Slim payload: never serialize the whole entity (its key embeds the UserEntity).
            trackingService.trackEvent("debug-runner-modified",
                    Map.of("componentName", componentName, "source", sourceCode));
        }
    }

    public void resetRunnersForUser(String userId, Collection<String> componentNames) {
        userDebugMainRepository.deleteAllByUserAndComponents(userId, componentNames);
    }
}