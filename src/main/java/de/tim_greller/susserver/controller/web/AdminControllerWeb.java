package de.tim_greller.susserver.controller.web;

import de.tim_greller.susserver.service.auth.UserGeneratorService;
import de.tim_greller.susserver.service.tracking.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AdminControllerWeb {

    private final UserGeneratorService userGeneratorService;
    private final SurveyService surveyService;

    @PostMapping("/admin/create-accounts")
    public String createAccounts(@Param("amount") int amount, RedirectAttributes redirectAttributes) {
        var createdUsers = userGeneratorService.createAccounts(amount);
        redirectAttributes.addFlashAttribute("createdUsers", createdUsers);
        return "redirect:/admin";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("surveyUrl", surveyService.getFinalSurveyUrl());
        model.addAttribute("showSurvey", surveyService.isSurveyActive());
        return "admin";
    }

    @PostMapping("/admin/survey")
    public String surveyUrl(@Param("surveyUrl") String surveyUrl,
                            @Param("showSurvey") boolean showSurvey) {
        surveyService.setSurveyUrl(surveyUrl);
        surveyService.setSurveyActive(showSurvey);
        return "redirect:/admin";
    }
}
