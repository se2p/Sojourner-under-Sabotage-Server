package de.tim_greller.susserver.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserGameProgressionDTO {
    private int id;
    private int room;
    private String componentName;
    private int stage;
    private GameProgressStatus status;
    private GameMode mode;
}
