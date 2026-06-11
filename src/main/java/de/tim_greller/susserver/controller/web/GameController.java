package de.tim_greller.susserver.controller.web;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.persistence.entity.ComponentEntity;
import de.tim_greller.susserver.persistence.repository.ComponentRepository;
import de.tim_greller.susserver.service.game.ActiveGameModeService;
import de.tim_greller.susserver.service.game.GameProgressionService;
import de.tim_greller.susserver.service.tracking.SurveyService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameProgressionService gameProgressionService;
    private final SurveyService surveyService;
    private final ComponentRepository componentRepository;

    @GetMapping({"/", "/home"})
    public String home(Model model) {
        model.addAttribute("hasTestingSave", gameProgressionService.hasSavedProgression(GameMode.Testing));
        model.addAttribute("hasDebuggingSave", gameProgressionService.hasSavedProgression(GameMode.Debugging));
        return "home";
    }

    @GetMapping("/reset")
    public String newGame() {
        gameProgressionService.resetGameProgression(GameMode.Testing);
        return "redirect:/game?mode=Testing";
    }

    @GetMapping("/reset-debug")
    public String newDebugGame() {
        gameProgressionService.resetGameProgression(GameMode.Debugging);
        return "redirect:/game?mode=Debugging";
    }

    @GetMapping("/game")
    public String game(@RequestParam(defaultValue = "Testing") String mode, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        model.addAttribute("gameMode", ActiveGameModeService.parseMode(mode).name());
        var showSurvey = surveyService.isSurveyActive();
        model.addAttribute("showSurvey", showSurvey);
        return "game";
    }

    // Standalone debug editor (debug.html); not part of the game flow.
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