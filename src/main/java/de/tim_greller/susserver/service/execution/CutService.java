package de.tim_greller.susserver.service.execution;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import de.tim_greller.susserver.dto.CutSourceDTO;
import de.tim_greller.susserver.dto.Range;
import de.tim_greller.susserver.persistence.entity.ActivePatchEntity;
import de.tim_greller.susserver.persistence.entity.ComponentEntity;
import de.tim_greller.susserver.persistence.entity.Patch;
import de.tim_greller.susserver.persistence.entity.PatchEntity;
import de.tim_greller.susserver.persistence.entity.UserEntity;
import de.tim_greller.susserver.persistence.entity.UserModifiedCutEntity;
import de.tim_greller.susserver.persistence.keys.ComponentKey;
import de.tim_greller.susserver.persistence.keys.ComponentStageKey;
import de.tim_greller.susserver.persistence.keys.UserComponentKey;
import de.tim_greller.susserver.persistence.repository.ActivePatchRepository;
import de.tim_greller.susserver.persistence.repository.ComponentRepository;
import de.tim_greller.susserver.persistence.repository.CutRepository;
import de.tim_greller.susserver.persistence.repository.PatchRepository;
import de.tim_greller.susserver.persistence.repository.UserModifiedCutRepository;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.tracking.UserEventTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CutService {

    private final PatchService patchService;
    private final UserEventTrackingService trackingService;
    private final CutRepository cutRepository;
    private final ComponentRepository componentRepository;
    private final PatchRepository patchRepository;
    private final ActivePatchRepository activePatchRepository;
    private final UserModifiedCutRepository userModifiedCutRepository;
    private final UserService userService;

    public Optional<CutSourceDTO> getOriginalCutForComponent(String componentName) {
        final ComponentEntity component = componentRepository.findById(componentName).orElseThrow();
        return cutRepository.findById(new ComponentKey(component))
                .map(CutSourceDTO::fromCutEntity);
    }

    static CutSourceDTO restrictToMethodBody(CutSourceDTO cut) {
        final String[] lines = cut.getSourceCode().split("\n");
        int depth = 0;
        int startLine = 0;
        int startColumn = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("{")) {
                if (depth == 1) {
                    startLine = i + 1;
                    startColumn = line.indexOf("{") + 2;
                }
                depth++;
            }
            if (line.contains("}")) {
                depth--;
                if (depth == 1) {
                    cut.restrictTo(new Range(
                            startLine, startColumn,
                            i + 1, line.indexOf("}") + 1
                    ));
                }
            }
        }

        return cut;
    }

    public Optional<CutSourceDTO> getCurrentCutForComponent(String componentName) {
        Optional<CutSourceDTO> originalCut = getOriginalCutForComponent(componentName);
        Optional<ActivePatchEntity> activePatch = activePatchRepository.findByKey(componentName, userService.requireCurrentUserId());
        Optional<UserModifiedCutEntity> userMod = userModifiedCutRepository.findByKey(componentName, userService.requireCurrentUserId());

        if (originalCut.isEmpty()) {
            return Optional.empty();
        }

        final CutSourceDTO cut = originalCut.get();
        if (userMod.isPresent()) {
            return userMod.map(patch -> applyPatch(cut, patch)).map(CutService::restrictToMethodBody);
        }
        final CutSourceDTO patchedCut = activePatch
                .map(ActivePatchEntity::getPatch)
                .map(patch -> applyPatch(cut, patch))
                .orElse(cut);
        return Optional.of(patchedCut).map(CutService::restrictToMethodBody);
    }

    public boolean isUserModified(String componentName) {
        return userModifiedCutRepository.findByKey(componentName, userService.requireCurrentUserId()).isPresent();
    }

    @Deprecated
    public void storePatch(String componentName, int stage,  String newSource) {
        ComponentEntity component = componentRepository.findById(componentName).orElseThrow();
        CutSourceDTO cut = getOriginalCutForComponent(componentName).orElseThrow();
        String patch = patchService.createPatch(cut.getSourceCode(), newSource);
        patchRepository.save(new PatchEntity(patch, new ComponentStageKey(component, stage)));
    }

    /**
     * Stores a user modification of the CUT source code as a patch compared to the original CUT.
     * <p>
     * TODO: maybe base patch on mutated CUT.
     *       (problem: 2 separate methods, once apply all patches, once only the mutation)
     *
     * @param componentName The name of the component to store the modification for.
     * @param newSource The source code of the CUT after the user modification.
     */
    public void storeUserModification(String componentName, String newSource) {
        ComponentEntity component = componentRepository.findById(componentName).orElseThrow();
        CutSourceDTO cut = getOriginalCutForComponent(componentName).orElseThrow();
        UserEntity user = userService.requireCurrentUser();
        String patch = patchService.createPatch(cut.getSourceCode(), newSource);
        String oldPatch = userModifiedCutRepository.findByKey(componentName, user.getUsername())
                .map(UserModifiedCutEntity::getPatch)
                .orElse(null);
        if (Objects.equals(oldPatch, patch)) return;

        var entity = UserModifiedCutEntity.builder()
                .patch(patch)
                .userComponentKey(new UserComponentKey(component, user))
                .build();

        userModifiedCutRepository.save(entity);
        trackingService.trackEvent("cut-modified", entity);
    }

    public void removeUserModification(String componentName) {
        userModifiedCutRepository.deleteByKey(componentName, userService.requireCurrentUserId());
    }

    public void resetCutsForUser(String userId, Collection<String> componentNames) {
        userModifiedCutRepository.deleteAllByUserAndComponents(userId, componentNames);
    }

    private CutSourceDTO applyPatch(CutSourceDTO cut, Patch patch) {
        String newSource = patchService.applyPatch(cut.getSourceCode(), patch.getPatch());
        cut.setSourceCode(newSource);
        return cut;
    }

    public CutSourceDTO resetCut(String componentName) {
        userModifiedCutRepository.deleteByKey(componentName, userService.requireCurrentUserId());
        return getCurrentCutForComponent(componentName).orElseThrow();
    }
}
