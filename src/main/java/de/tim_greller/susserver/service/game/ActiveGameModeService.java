package de.tim_greller.susserver.service.game;

import java.util.Arrays;
import java.util.Optional;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.persistence.keys.UserModeKey;
import de.tim_greller.susserver.service.auth.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Holds the game mode the current request or STOMP message is acting on.
 **/
@Service
@RequiredArgsConstructor
public class ActiveGameModeService {

    private static final ThreadLocal<GameMode> boundMode = new ThreadLocal<>();

    private final UserService userService;

    public void bindMode(GameMode mode) {
        boundMode.set(mode);
    }
    
    public void bindMode(String modeName) {
        bindMode(parseMode(modeName));
    }

    public void clearMode() {
        boundMode.remove();
    }

    public GameMode getModeForCurrentUser() {
        return Optional.ofNullable(boundMode.get()).orElse(GameMode.Testing);
    }
    
    public UserModeKey currentUserModeKey() {
        return new UserModeKey(userService.requireCurrentUser(), getModeForCurrentUser());
    }

    public static GameMode parseMode(String modeName) {
        return Arrays.stream(GameMode.values())
                .filter(m -> m.name().equalsIgnoreCase(modeName))
                .findFirst()
                .orElse(GameMode.Testing);
    }
}