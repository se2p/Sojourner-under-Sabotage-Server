package de.tim_greller.susserver.persistence.entity;

import static de.tim_greller.susserver.dto.GameProgressStatus.TALK;
import static jakarta.persistence.FetchType.EAGER;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.dto.GameProgressStatus;
import de.tim_greller.susserver.persistence.keys.UserModeKey;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGameProgressionEntity {

    @EmbeddedId
    private UserModeKey id;

    @ManyToOne(fetch = EAGER)
    private GameProgressionEntity gameProgression;

    @Builder.Default
    private GameProgressStatus status = TALK;

    public GameMode getMode() {
        return id.getMode();
    }

}