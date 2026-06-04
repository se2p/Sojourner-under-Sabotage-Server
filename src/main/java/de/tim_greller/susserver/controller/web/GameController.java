package de.tim_greller.susserver.controller.web;

import de.tim_greller.susserver.persistence.entity.ComponentEntity;
import de.tim_greller.susserver.persistence.repository.ComponentRepository;
import de.tim_greller.susserver.service.game.GameProgressionService;
import de.tim_greller.susserver.service.tracking.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameProgressionService gameProgressionService;
    private final SurveyService surveyService;
    private final ComponentRepository componentRepository;

    @GetMapping("/reset")
    public String newGame() {
        gameProgressionService.resetGameProgression();
        return "redirect:/game";
    }

    @GetMapping("/game")
    public String game(Model model) {
        var showSurvey = surveyService.isSurveyActive();
        model.addAttribute("showSurvey", showSurvey);
        return "game";
    }
    //todo remove/rework used for testing
    @GetMapping("/debug")
    public String debugEditor(Model model) {
        var components = componentRepository.findAll()
                .stream()
                .map(ComponentEntity::getName)
                .sorted()
                .toList();
        model.addAttribute("components", components);
        return "debug";
    }
}
