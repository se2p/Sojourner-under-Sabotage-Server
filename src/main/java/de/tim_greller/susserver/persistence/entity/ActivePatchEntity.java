package de.tim_greller.susserver.persistence.entity;

import de.tim_greller.susserver.persistence.keys.UserComponentKey;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ActivePatchEntity {

    @EmbeddedId
    private UserComponentKey userComponentKey;

    @ManyToOne
    @JoinColumns({@JoinColumn, @JoinColumn})
    private PatchEntity patch;
}
