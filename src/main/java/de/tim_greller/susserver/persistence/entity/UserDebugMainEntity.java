package de.tim_greller.susserver.persistence.entity;

import de.tim_greller.susserver.persistence.keys.UserComponentKey;
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
public class UserDebugMainEntity {

    @EmbeddedId
    private UserComponentKey userComponentKey;

    @Column(length = 100_000)
    @Setter
    private String sourceCode;
}