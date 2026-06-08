package de.tim_greller.susserver.persistence.entity;

import de.tim_greller.susserver.dto.GameMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The strand this step belongs to. Together with {@link #orderIndex} it identifies a step. */
    @Enumerated(EnumType.STRING)
    private GameMode mode;

    /** Position of this step within its strand, starting at 1. Not globally unique across strands. */
    private int orderIndex;

    private int roomId;

    @ManyToOne
    private ComponentEntity component;

    private int stage;

    private int delaySeconds;
}
