package de.tim_greller.susserver.persistence.entity;

import de.tim_greller.susserver.persistence.keys.ComponentStageKey;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DebugMainEntity {

    @EmbeddedId
    ComponentStageKey componentStageKey;

    @Column
    private String className;

    @Column(length = 100_000)
    @Setter
    private String sourceCode;
}