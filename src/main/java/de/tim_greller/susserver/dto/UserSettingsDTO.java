package de.tim_greller.susserver.dto;

import de.tim_greller.susserver.persistence.entity.UserEntity;
import de.tim_greller.susserver.persistence.entity.UserSettingsEntity;
import de.tim_greller.susserver.persistence.keys.UserKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class UserSettingsDTO {
    private boolean codeEditorIntroductionShown;
    private String lastSurvey;

    public static UserSettingsDTO fromEntity(UserSettingsEntity userSettingsEntity) {
        return UserSettingsDTO.builder()
                .codeEditorIntroductionShown(userSettingsEntity.isCodeEditorIntroductionShown())
                .lastSurvey(userSettingsEntity.getLastSurvey())
                .build();
    }

    public UserSettingsEntity toEntity(UserEntity user) {
        return UserSettingsEntity.builder()
                .user(new UserKey(user))
                .codeEditorIntroductionShown(codeEditorIntroductionShown)
                .lastSurvey(lastSurvey)
                .build();
    }
}
