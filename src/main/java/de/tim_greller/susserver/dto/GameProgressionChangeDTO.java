package de.tim_greller.susserver.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Tracking details of a single game progression change, used to reconstruct the
 * duration of each phase from the recorded timestamps.
 */
@Data
@Builder
public class GameProgressionChangeDTO {
    private int orderIndex;
    private int room;
    private int stage;
    private String componentName;
    private GameProgressStatus previousStatus;
    private GameProgressStatus status;
    private GameMode mode;
}
