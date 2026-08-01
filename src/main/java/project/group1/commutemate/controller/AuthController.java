package project.group1.commutemate.controller;

import java.util.Optional;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Role;

/**
 * Renders the sign-in / sign-up page (Epic 1).
 *
 * <p>Submission is handled elsewhere: login POSTs to {@code /login}
 * (Spring Security form login, see {@code SecurityConfig}) and sign-up
 * POSTs to {@code /register} (see {@code RegisterController}).</p>
 */
@Controller
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/auth")
    public String authPage(@RequestParam(defaultValue = "login") String mode,
                           @RequestParam(required = false) String error,
                           @RequestParam(required = false) String registered,
                           HttpSession session,
                           Model model) {
        // Already signed in — go to the member's own dashboard instead
        Optional<Profile> profile = currentUserService.currentProfile();
        if (profile.isPresent()) {
            return profile.get().getRole() == Role.DRIVER
                    ? "redirect:/dashboard/driver"
                    : "redirect:/dashboard/rider";
        }

        model.addAttribute("authenticated", false);
        model.addAttribute("mode", "signup".equals(mode) ? "signup" : "login");
        model.addAttribute("selectedRole", "rider");
        if (error != null) {
            model.addAttribute("error", "Invalid email or password");
            model.addAttribute("loginError", true);
            Object lastEmail = session.getAttribute("LAST_LOGIN_EMAIL");
            if (lastEmail instanceof String email) {
                model.addAttribute("email", email);
            }
            session.removeAttribute("LAST_LOGIN_EMAIL");
        }
        if (registered != null) {
            model.addAttribute("success", "Account created — log in with your SFU email.");
        }
        return "auth";
    }
}
