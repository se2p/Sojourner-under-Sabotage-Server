package de.tim_greller.susserver.events;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tim_greller.susserver.dto.GameMode;
import lombok.Getter;
import lombok.Setter;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "type")
public class Event {
    private final long timestamp = System.currentTimeMillis();
    
    @Setter
    private GameMode mode;
}
