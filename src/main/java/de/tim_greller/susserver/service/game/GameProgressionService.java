package de.tim_greller.susserver.service.game;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static de.tim_greller.susserver.dto.GameProgressStatus.DEBUGGING;
import static de.tim_greller.susserver.dto.GameProgressStatus.DESTROYED;
import static de.tim_greller.susserver.dto.GameProgressStatus.DOOR;
import static de.tim_greller.susserver.dto.GameProgressStatus.MUTATED;
import static de.tim_greller.susserver.dto.GameProgressStatus.PUZZLE;
import static de.tim_greller.susserver.dto.GameProgressStatus.TALK;
import static de.tim_greller.susserver.dto.GameProgressStatus.TEST;
import static de.tim_greller.susserver.dto.GameProgressStatus.TESTS_ACTIVE;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.dto.GameProgressStatus;
import de.tim_greller.susserver.dto.GameProgressionChangeDTO;
import de.tim_greller.susserver.dto.UserGameProgressionDTO;
import de.tim_greller.susserver.events.ComponentDestroyedEvent;
import de.tim_greller.susserver.events.ComponentFixedEvent;
import de.tim_greller.susserver.events.ComponentTestsActivatedEvent;
import de.tim_greller.susserver.events.ConversationFinishedEvent;
import de.tim_greller.susserver.events.DebugStartEvent;
import de.tim_greller.susserver.events.GameFinishedEvent;
import de.tim_greller.susserver.events.GameProgressionChangedEvent;
import de.tim_greller.susserver.events.GameStartedEvent;
import de.tim_greller.susserver.events.MutatedComponentTestsFailedEvent;
import de.tim_greller.susserver.events.PuzzleSolvedEvent;
import de.tim_greller.susserver.events.RoomUnlockedEvent;
import de.tim_greller.susserver.events.TempleEnteredEvent;
import de.tim_greller.susserver.persistence.entity.GameProgressionEntity;
import de.tim_greller.susserver.persistence.entity.UserEntity;
import de.tim_greller.susserver.persistence.entity.UserGameProgressionEntity;
import de.tim_greller.susserver.persistence.keys.UserModeKey;
import de.tim_greller.susserver.persistence.repository.GameProgressionRepository;
import de.tim_greller.susserver.persistence.repository.UserGameProgressionRepository;
import de.tim_greller.susserver.service.auth.UserService;
import de.tim_greller.susserver.service.execution.CutService;
import de.tim_greller.susserver.service.execution.DebugMainService;
import de.tim_greller.susserver.service.execution.TestService;
import de.tim_greller.susserver.service.tracking.UserEventTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class GameProgressionService {

    private final UserGameProgressionRepository userGameProgressionRepository;
    private final GameProgressionRepository gameProgressionRepository;
    private final ComponentStatusService componentStatusService;
    private final AttackService attackService;
    private final TestService testService;
    private final DebugMainService debugMainService;
    private final CutService cutService;
    private final UserService userService;
    private final EventService eventService;
    private final UserSettingsService userSettingsService;
    private final ActiveGameModeService activeModeService;
    private final UserEventTrackingService trackingService;


    // Instantiated by the Spring IoC container during startup even if not injected anywhere.
    // It then registers itself as a handler for the ComponentTestsActivatedEvent.
    GameProgressionService(EventService eventService, UserGameProgressionRepository userGameProgressionRepository,
                           GameProgressionRepository gameProgressionRepository,
                           ComponentStatusService componentStatusService, AttackService attackService,
                           TestService testService, DebugMainService debugMainService, CutService cutService,
                           UserService userService, UserSettingsService userSettingsService,
                           ActiveGameModeService activeModeService, UserEventTrackingService trackingService) {
        this.userGameProgressionRepository = userGameProgressionRepository;
        this.gameProgressionRepository = gameProgressionRepository;
        this.componentStatusService = componentStatusService;
        this.attackService = attackService;
        this.testService = testService;
        this.debugMainService = debugMainService;
        this.cutService = cutService;
        this.userService = userService;
        this.eventService = eventService;
        this.userSettingsService = userSettingsService;
        this.activeModeService = activeModeService;
        this.trackingService = trackingService;

        eventService.registerHandler(GameStartedEvent.class, this::handleGameStarted);
        eventService.registerHandler(RoomUnlockedEvent.class, this::handleRoomUnlocked);
        eventService.registerHandler(ConversationFinishedEvent.class, this::handleConversationFinished);
        eventService.registerHandler(ComponentTestsActivatedEvent.class, this::handleComponentTestsActivated);
        eventService.registerHandler(ComponentDestroyedEvent.class, this::handleComponentDestroyed);
        eventService.registerHandler(MutatedComponentTestsFailedEvent.class, this::handleMutatedComponentTestsFailed);
        eventService.registerHandler(DebugStartEvent.class, this::handleDebugStart);
        eventService.registerHandler(ComponentFixedEvent.class, this::handleComponentFixed);
        eventService.registerHandler(PuzzleSolvedEvent.class, this::handlePuzzleSolved);
        eventService.registerHandler(TempleEnteredEvent.class, this::handleTempleEntered);
    }

    public void handleComponentTestsActivated(ComponentTestsActivatedEvent event) {
        UserGameProgressionEntity userGameProgression = userGameProgressionRepository.findById(currentUserModeKey()).orElseThrow();
        if (userGameProgression.getStatus() != TEST) {
            log.warn("Received ComponentTestsActivatedEvent while not in TEST state.");
            return;
        }
        if (componentStatusService.handleComponentTestsActivated(event)) {
            userGameProgression.setStatus(TESTS_ACTIVE);
            userGameProgressionRepository.save(userGameProgression);
            changeGameProgression(userGameProgression, TEST);
            gameLoop(userGameProgression);
        }
    }

    private void handleGameStarted(GameStartedEvent gameStartedEvent) {
        var gameProgression = userGameProgressionRepository
                .findById(currentUserModeKey())
                .orElseGet(() -> {
                    var user = userService.requireCurrentUser();
                    var mode = activeModeService.getModeForCurrentUser();
                    log.info("Game started for user {} without progression in mode {}, initializing.",
                            user.getUsername(), mode);
                    return initGameProgression(user, mode);
                });

        final var statusBeforeGameStart = gameProgression.getStatus();

        // handle TESTS_ACTIVE state
        gameLoop(gameProgression);

        // handle DESTROYED and MUTATED states
        if (isTesting(gameProgression) && List.of(DESTROYED, MUTATED, DEBUGGING).contains(gameProgression.getStatus())) {
            // RESET game progression to TEST_ACTIVE, so that test failures will trigger
            gameProgression.setStatus(TESTS_ACTIVE);
            userGameProgressionRepository.save(gameProgression);
            attackService.attackCut(gameProgression.getGameProgression().getComponent().getName());
        } else {
            // Re-fetch: gameLoop may have attacked the component and advanced the status meanwhile.
            changeGameProgression(userGameProgressionRepository.findById(currentUserModeKey()).orElseThrow(),
                    statusBeforeGameStart);
        }
    }

    /**
     * Bump user game progression. Update the user component status.
     * @param componentFixedEvent the event
     */
    private void handleComponentFixed(ComponentFixedEvent componentFixedEvent) {
        log.info("handle componentFixedEvent: {}", componentFixedEvent);

        // The event is published on every passing verification run; it only advances the game while
        // the progression is actually in the DEBUGGING phase
        var userProgressOpt = userGameProgressionRepository.findById(currentUserModeKey());
        if (userProgressOpt.isEmpty() || userProgressOpt.get().getStatus() != DEBUGGING) {
            log.info("Ignoring ComponentFixedEvent outside the DEBUGGING phase.");
            return;
        }
        var userProgress = userProgressOpt.get();
        var progression = userProgress.getGameProgression();

        if (!Objects.equals(progression.getComponent().getName(), componentFixedEvent.getComponentName())) {
            log.warn("Received ComponentFixedEvent for wrong component.");
            return;
        }

        var newProgressionOpt = gameProgressionRepository
                .findNextProgression(progression.getMode(), progression.getOrderIndex());
        if (newProgressionOpt.isEmpty()) {
            // TODO: handle game finished on max level reached
            eventService.publishEvent(new GameFinishedEvent());
            return;
        }
        var newProgression = newProgressionOpt.get();

        final var previousStatus = userProgress.getStatus();
        userProgress.setGameProgression(newProgression);
        var nextRoomStatus = isDebugging(userProgress) || newProgression.getStage() == 1 ? DOOR : TESTS_ACTIVE;
        userProgress.setStatus(nextRoomStatus);
        userGameProgressionRepository.save(userProgress);
        changeGameProgression(userProgress, previousStatus);
        log.info("componentFixedEvent changed game progression to: {}", newProgression);

        // Set the component stage
        componentStatusService.getComponentStatus(newProgression.getComponent().getName(), userService.requireCurrentUserId())
                .setStage(newProgression.getStage());
    }

    private void handleRoomUnlocked(RoomUnlockedEvent roomUnlockedEvent) {
        var gameProgression = userGameProgressionRepository.findById(currentUserModeKey()).orElseThrow();
        var roomIdMatches = gameProgression.getGameProgression().getRoomId() == roomUnlockedEvent.getRoomId();
        if (gameProgression.getStatus() == DOOR && roomIdMatches) {
            gameProgression.setStatus(TALK);
            userGameProgressionRepository.save(gameProgression);
            changeGameProgression(gameProgression, DOOR);
        }
    }

    private void handleConversationFinished(ConversationFinishedEvent conversationFinishedEvent) {
        UserGameProgressionEntity userGameProgression = userGameProgressionRepository.findById(currentUserModeKey()).orElseThrow();
        // Debug strand: TALK covers the whole planet-outside phase and only ends when the player
        // walks into the temple (TempleEnteredEvent), not when the arrival conversation ends.
        if (userGameProgression.getStatus() == TALK && !isDebugging(userGameProgression)) {
            userGameProgression.setStatus(TEST);
            userGameProgressionRepository.save(userGameProgression);
            changeGameProgression(userGameProgression, TALK);
        }
    }

    private void handleTempleEntered(TempleEnteredEvent templeEnteredEvent) {
        var gameProgression = userGameProgressionRepository.findById(currentUserModeKey()).orElseThrow();
        if (isDebugging(gameProgression) && gameProgression.getStatus() == TALK) {
            gameProgression.setStatus(PUZZLE);
            userGameProgressionRepository.save(gameProgression);
            changeGameProgression(gameProgression, TALK);
        }
    }

    private void handlePuzzleSolved(PuzzleSolvedEvent puzzleSolvedEvent) {
        var gameProgression = userGameProgressionRepository.findById(currentUserModeKey()).orElseThrow();
        if (isDebugging(gameProgression) && gameProgression.getStatus() == PUZZLE) {
            gameProgression.setStatus(DEBUGGING);
            userGameProgressionRepository.save(gameProgression);
            changeGameProgression(gameProgression, PUZZLE);
        }
    }

    private void handleComponentMutated(String componentName, GameProgressStatus targetStatus) {
        var gameProgression = userGameProgressionRepository.findById(currentUserModeKey()).orElseThrow();
        var componentMatches = gameProgression.getGameProgression().getComponent().getName().equals(componentName);
        if (gameProgression.getStatus() == TESTS_ACTIVE && componentMatches) {
            gameProgression.setStatus(targetStatus);
            userGameProgressionRepository.save(gameProgression);
            changeGameProgression(gameProgression, TESTS_ACTIVE);
        }
    }

    private void handleMutatedComponentTestsFailed(MutatedComponentTestsFailedEvent mutatedComponentTestsFailedEvent) {
        handleComponentMutated(mutatedComponentTestsFailedEvent.getComponentName(), MUTATED);
    }

    private void handleComponentDestroyed(ComponentDestroyedEvent componentDestroyedEvent) {
        handleComponentMutated(componentDestroyedEvent.getComponentName(), DESTROYED);
    }

    private void handleDebugStart(DebugStartEvent debugStartEvent) {
        var gameProgression = userGameProgressionRepository.findById(currentUserModeKey()).orElseThrow();
        var componentMatches = gameProgression.getGameProgression().getComponent().getName().equals(debugStartEvent.getComponentName());
        if (gameProgression.getStatus().readyForDebugging() && componentMatches) {
            final var previousStatus = gameProgression.getStatus();
            gameProgression.setStatus(DEBUGGING);
            userGameProgressionRepository.save(gameProgression);
            changeGameProgression(gameProgression, previousStatus);
        }
    }

    private void gameLoop(UserGameProgressionEntity gameProgression) {
        if (gameProgression.getStatus() == TESTS_ACTIVE) {
            String componentName = gameProgression.getGameProgression().getComponent().getName();
            int waitDurationSeconds = gameProgression.getGameProgression().getDelaySeconds();
            log.info("Waiting for {} seconds before attacking component {}", waitDurationSeconds, componentName);
            try {
                Thread.sleep(waitDurationSeconds * 1_000L);
            } catch (InterruptedException e) {
                log.error("Game loop was interrupted.");
                Thread.currentThread().interrupt();
            }
            attackService.attackCut(componentName);
        }
    }

    // Only resets the data of the given mode's components, so the other strand's progress is kept.
    @Transactional
    public void resetGameProgression(GameMode mode) {
        var userId = userService.requireCurrentUserId();
        var componentNames = gameProgressionRepository.findComponentNamesByMode(mode);
        componentStatusService.resetComponentStatus(userId, componentNames);
        attackService.resetAttacks(userId, componentNames);
        testService.resetTestsForUser(userId, componentNames);
        debugMainService.resetRunnersForUser(userId, componentNames);
        cutService.resetCutsForUser(userId, componentNames);
        initGameProgression(userService.requireCurrentUser(), mode);
        userSettingsService.resetUserSettings();
    }

    public UserGameProgressionEntity initGameProgression(UserEntity user, GameMode mode) {
        var initialStatus = GameMode.Debugging.equals(mode) ? DOOR : TALK;
        var gameProgression = UserGameProgressionEntity.builder()
                .gameProgression(firstProgressionOf(mode))
                .status(initialStatus)
                .id(new UserModeKey(user, mode))
                .build();
        return userGameProgressionRepository.save(gameProgression);
    }

    private GameProgressionEntity firstProgressionOf(GameMode mode) {
        return gameProgressionRepository.findFirstProgression(mode)
                .orElseThrow(() -> new IllegalStateException("No game progression configured for mode " + mode));
    }

    public Optional<UserGameProgressionDTO> getCurrentGameProgression() {
        return userGameProgressionRepository.findById(currentUserModeKey()).map(this::toDTO);
    }

    // Whether the current user has a saved game for the given mode. False for anonymous users.
    public boolean hasSavedProgression(GameMode mode) {
        return userService.getCurrentUserId()
                .map(userId -> userGameProgressionRepository.existsByIdUserUsernameAndIdMode(userId, mode))
                .orElse(false);
    }

    private UserModeKey currentUserModeKey() {
        return activeModeService.currentUserModeKey();
    }

    private static boolean isDebugging(UserGameProgressionEntity ugp) {
        return GameMode.Debugging.equals(ugp.getMode());
    }

    private static boolean isTesting(UserGameProgressionEntity ugp) {
        return GameMode.Testing.equals(ugp.getMode());
    }

    private UserGameProgressionDTO toDTO(UserGameProgressionEntity ugp) {
        return UserGameProgressionDTO.builder()
                .id(ugp.getGameProgression().getOrderIndex())
                .room(ugp.getGameProgression().getRoomId())
                .componentName(ugp.getGameProgression().getComponent().getName())
                .stage(ugp.getGameProgression().getStage())
                .status(ugp.getStatus())
                .mode(ugp.getMode() == null ? GameMode.Testing : ugp.getMode())
                .build();
    }

    private void changeGameProgression(UserGameProgressionEntity ugp, GameProgressStatus previousStatus) {
        trackingService.trackEvent("game-progression-changed", toChangeDTO(ugp, previousStatus));
        eventService.publishEvent(new GameProgressionChangedEvent(toDTO(ugp)));
    }

    private GameProgressionChangeDTO toChangeDTO(UserGameProgressionEntity ugp, GameProgressStatus previousStatus) {
        return GameProgressionChangeDTO.builder()
                .orderIndex(ugp.getGameProgression().getOrderIndex())
                .room(ugp.getGameProgression().getRoomId())
                .stage(ugp.getGameProgression().getStage())
                .componentName(ugp.getGameProgression().getComponent().getName())
                .previousStatus(previousStatus)
                .status(ugp.getStatus())
                .mode(ugp.getMode() == null ? GameMode.Testing : ugp.getMode())
                .build();
    }
}
