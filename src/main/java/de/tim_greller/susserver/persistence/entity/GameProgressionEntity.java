package de.tim_greller.susserver.persistence.entity;

import de.tim_greller.susserver.dto.GameMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GameProgressionEntity {
    /** The strand this step belongs to. Used to query the right progression sequence. */
    @Enumerated(EnumType.STRING)
    private GameMode mode;

    /** Globally unique step position: testing strand starts at 1, debugging strand at 1001. */
    @Id
    private int orderIndex;

    private int roomId;

    @ManyToOne
    private ComponentEntity component;

    private int stage;

    private int delaySeconds;
}
