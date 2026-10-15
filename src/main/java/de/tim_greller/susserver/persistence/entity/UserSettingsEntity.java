package de.tim_greller.susserver.persistence.entity;

import de.tim_greller.susserver.persistence.keys.UserKey;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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
public class UserSettingsEntity {
    @EmbeddedId private UserKey user;

    @Builder.Default
    private boolean codeEditorIntroductionShown = false;

    // CSV of debug-strand room numbers whose intro popup was already shown (e.g. "1,2").
    @Builder.Default
    private String debugRoomIntrosShown = "";
}
