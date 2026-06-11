package de.tim_greller.susserver.service.game;

import java.util.concurrent.ConcurrentHashMap;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.persistence.keys.UserModeKey;
import de.tim_greller.susserver.service.auth.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActiveGameModeService {

    private final UserService userService;
    private final ConcurrentHashMap<String, GameMode> activeModes = new ConcurrentHashMap<>();

    public void setMode(String userId, GameMode mode) {
        activeModes.put(userId, mode);
    }

    public GameMode getModeForCurrentUser() {
        return activeModes.getOrDefault(userService.requireCurrentUserId(), GameMode.Testing);
    }

    // The current user's progression key for their active mode
    public UserModeKey currentUserModeKey() {
        return new UserModeKey(userService.requireCurrentUser(), getModeForCurrentUser());
    }

}