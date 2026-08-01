package project.group1.commutemate.User;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import project.group1.commutemate.model.Role;

@Controller
public class RegisterController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public String processRegistration(@Valid @ModelAttribute("registerRequest") RegisterRequest registerRequest,
                                       BindingResult bindingResult,
                                       Model model) {

        String email = registerRequest.getEmail() == null
                ? ""
                : registerRequest.getEmail().trim().toLowerCase(Locale.ROOT);

        if (!email.isEmpty() && userRepository.findByEmailIgnoreCase(email).isPresent()) {
            bindingResult.rejectValue("email", "duplicate", "An account with this email already exists");
        }

        if (bindingResult.hasErrors()) {
            // Re-render the shared auth page in sign-up mode with the first problem shown
            model.addAttribute("authenticated", false);
            model.addAttribute("mode", "signup");
            model.addAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            model.addAttribute("fullName", registerRequest.getFullName());
            model.addAttribute("email", registerRequest.getEmail());
            model.addAttribute("selectedRole", selectedRoleOrEmpty(registerRequest.getRole()));
            model.addAttribute("fieldErrors", fieldErrors(bindingResult));
            return "auth";
        }

        User user = new User();
        user.setFullName(registerRequest.getFullName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole(Role.from(registerRequest.getRole()));

        userRepository.save(user);

        return "redirect:/auth?mode=login&registered";
    }

    private Map<String, String> fieldErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }

    private String selectedRoleOrEmpty(String role) {
        if (role == null) {
            return "";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return "rider".equals(normalized) || "driver".equals(normalized)
                ? normalized
                : "";
    }
}
