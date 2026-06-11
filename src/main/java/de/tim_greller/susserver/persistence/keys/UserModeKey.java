package de.tim_greller.susserver.persistence.keys;

import java.io.Serializable;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.persistence.entity.UserEntity;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserModeKey implements Serializable {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    private GameMode mode;

}