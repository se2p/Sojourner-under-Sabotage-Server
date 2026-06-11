package de.tim_greller.susserver.service.execution;

import java.util.Collection;
import java.util.Optional;

import de.tim_greller.susserver.persistence.entity.ComponentEntity;
import de.tim_greller.susserver.persistence.entity.ComponentStatusEntity;
import de.tim_greller.susserver.persistence.entity.DebugMainEntity;
import de.tim_greller.susserver.persistence.entity.UserDebugMainEntity;
import de.tim_greller.susserver.persistence.entity.UserEntity;
import de.tim_greller.susserver.persistence.keys.UserComponentKey;
import de.tim_greller.susserver.persistence.repository.ComponentRepository;
import de.tim_greller.susserver.persistence.repository.ComponentStatusRepository;
import de.tim_greller.susserver.persistence.repository.DebugMainRepository;
import de.tim_greller.susserver.persistence.repository.UserDebugMainRepository;
import de.tim_greller.susserver.service.auth.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DebugMainService {

    private final DebugMainRepository debugMainRepository;
    private final UserDebugMainRepository userDebugMainRepository;
    private final ComponentRepository componentRepository;
    private final ComponentStatusRepository componentStatusRepository;
    private final UserService userService;

    // Return the runner the user should see: their saved edits if present,
    // otherwise the shared template for the component and the user's current stage.
    public Optional<String> getRunnerForUser(String componentName, String userId) {
        Optional<String> userRunner = userDebugMainRepository.findByKey(componentName, userId)
                .map(UserDebugMainEntity::getSourceCode);
        if (userRunner.isPresent()) {
            return userRunner;
        }
        int stage = componentStatusRepository.findByKey(componentName, userId)
                .map(ComponentStatusEntity::getStage)
                .orElse(1);
        return debugMainRepository.findByKey(componentName, stage)
                .map(DebugMainEntity::getSourceCode);
    }

    // Persist the user's runner edits for a component.
    public void saveRunnerForUser(String componentName, String userId, String sourceCode) {
        UserDebugMainEntity entity = userDebugMainRepository.findByKey(componentName, userId)
                .orElseGet(() -> {
                    final ComponentEntity component = componentRepository.findById(componentName).orElseThrow();
                    final UserEntity user = userService.loadUserByUsername(userId).orElseThrow();
                    return new UserDebugMainEntity(new UserComponentKey(component, user), sourceCode);
                });
        entity.setSourceCode(sourceCode);
        userDebugMainRepository.save(entity);
    }

    public void resetRunnersForUser(String userId, Collection<String> componentNames) {
        userDebugMainRepository.deleteAllByUserAndComponents(userId, componentNames);
    }
}