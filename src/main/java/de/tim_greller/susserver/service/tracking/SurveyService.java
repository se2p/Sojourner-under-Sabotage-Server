package de.tim_greller.susserver.service.tracking;

import de.tim_greller.susserver.persistence.entity.GlobalSettingsEntity;
import de.tim_greller.susserver.persistence.repository.GlobalSettingsRepository;
import de.tim_greller.susserver.service.game.UserSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyService {

    private static final String SURVEY_URL_KEY = "survey-url";
    private static final String SURVEY_ACTIVE_KEY = "survey-active";

    private static final String SURVEY_1 = "https://docs.google.com/forms/d/e/1FAIpQLSdji5-zEhzBdC1FgeOD24aVH50Cj6h13zioM09lEvTRgTqMhg/viewform?usp=pp_url";
    private static final String SURVEY_2 = "https://docs.google.com/forms/d/e/1FAIpQLSeAi-hGXaRprTaZ7O1E270j1mDgWmYDzwoQ_mn4-ckKijfBkg/viewform?usp=pp_url";

    private final UserSettingsService userSettingsService;
    private final GlobalSettingsRepository settingsRepository;

    public String getFinalSurveyUrl() {
        return settingsRepository.findById(SURVEY_URL_KEY)
                .map(GlobalSettingsEntity::getSettingsValue)
                .orElse(null);
    }


    public String getSurveyUrl() {
        var latestSurveyGlobal = settingsRepository.findById("latest-survey").map(GlobalSettingsEntity::getSettingsValue).orElse("2");
        var userSettings = userSettingsService.getUserSettings();
        System.out.println(userSettings);
        if (userSettings.getLastSurvey() == null) {
            // before game survey
            if (latestSurveyGlobal.equals("2")) {
                settingsRepository.save(GlobalSettingsEntity.builder().settingsKey("latest-survey").settingsValue("1").build());
                userSettings.setLastSurvey("1");
                userSettingsService.updateUserSettings(userSettings);
                return SURVEY_1 + "&entry.1784741425=Before+playing+the+game&entry.784200635=";
            } else {
                settingsRepository.save(GlobalSettingsEntity.builder().settingsKey("latest-survey").settingsValue("2").build());
                userSettings.setLastSurvey("2");
                userSettingsService.updateUserSettings(userSettings);
                return SURVEY_2 + "&entry.1784741425=Before+playing+the+game&entry.784200635=";
            }
        } else {
            // after game survey
            if (userSettings.getLastSurvey().equals("1")) {
                userSettings.setLastSurvey("f");
                userSettingsService.updateUserSettings(userSettings);
                return SURVEY_2 + "&entry.1784741425=After+playing+the+game&entry.784200635=";
            } else if (userSettings.getLastSurvey().equals("2")) {
                userSettings.setLastSurvey("f");
                userSettingsService.updateUserSettings(userSettings);
                return SURVEY_1 + "&entry.1784741425=After+playing+the+game&entry.784200635=";
            }  else if (userSettings.getLastSurvey().equals("f")) {
                // demographics & feedback survey
                return getFinalSurveyUrl();
            }
        }

        System.out.println("/// should be unreachable!");
        return getFinalSurveyUrl();
    }

    public String getSurveyName() {
        var userSettings = userSettingsService.getUserSettings();
        if (userSettings.getLastSurvey() == null) {
            return "Pre-Questionnaire";
        }  else {
            if (!userSettings.getLastSurvey().equals("f")) {
                return "Post-Questionnaire";
            } else {
                return "Feedback-Survey";
            }
        }
    }

    public void setSurveyUrl(String surveyUrl) {
        var entity = GlobalSettingsEntity.builder()
                .settingsKey(SURVEY_URL_KEY)
                .settingsValue(surveyUrl)
                .build();
        settingsRepository.save(entity);
    }

    public boolean isSurveyActive() {
        var active = settingsRepository.findById(SURVEY_ACTIVE_KEY)
                .map(GlobalSettingsEntity::getSettingsValue)
                .orElse(null);
        return Boolean.parseBoolean(active);
    }

    public void setSurveyActive(boolean active) {
        var entity = GlobalSettingsEntity.builder()
                .settingsKey(SURVEY_ACTIVE_KEY)
                .settingsValue(Boolean.toString(active))
                .build();
        settingsRepository.save(entity);
    }
}
