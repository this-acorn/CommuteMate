package project.group1.commutemate.controller;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Role;

/**
 * Public landing page for CommuteMate. Logged-in members are sent to
 * their dashboard instead of seeing the marketing page.
 */
@Controller
public class HomeController {

    private final CurrentUserService currentUserService;

    public HomeController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/")
    public String landing(Model model) {
        Optional<Profile> profile = currentUserService.currentProfile();
        if (profile.isPresent()) {
            return profile.get().getRole() == Role.DRIVER
                    ? "redirect:/dashboard/driver"
                    : "redirect:/dashboard/rider";
        }

        model.addAttribute("authenticated", false);
        return "index";
    }
}
