package de.tim_greller.susserver.controller.web;

import de.tim_greller.susserver.dto.UserRegistrationDTO;
import de.tim_greller.susserver.exception.UserAlreadyExistException;
import de.tim_greller.susserver.persistence.entity.UserEntity;
import de.tim_greller.susserver.service.auth.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequiredArgsConstructor
public class RegistrationController {

    private final UserService userService;


    @GetMapping("/register")
    public String showRegistrationForm(WebRequest request, Model model) {
        UserRegistrationDTO userDto = new UserRegistrationDTO();
        model.addAttribute("user", userDto);
        return "register";
    }

    @PostMapping("/register")
    public ModelAndView registerUserAccount(@ModelAttribute("user") @Valid UserRegistrationDTO userDto,
                                            BindingResult result, ModelAndView mav) {
        if (result.hasErrors()) {
            return mav;
        }

        try {
            UserEntity registered = userService.registerNewUserAccount(userDto);
            return new ModelAndView("login", "userRegistered", registered);
        } catch (UserAlreadyExistException uaeEx) {
            result.addError(new FieldError("user", "username",
                    "The user " + userDto.getUsername() + " already exists."));
            return mav;
        }
    }
}
