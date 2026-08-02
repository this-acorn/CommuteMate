package project.group1.commutemate.controller;

import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.User.UpdateNameRequest;
import project.group1.commutemate.User.UpdatePasswordRequest;
import project.group1.commutemate.User.User;
import project.group1.commutemate.User.UserRepository;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.service.NotificationService;

@Controller
public class ProfileController extends AuthenticatedController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              CurrentUserService currentUserService,
                              NotificationService notificationService) {
        super(currentUserService,notificationService);
        this.userRepository =userRepository;
        this.passwordEncoder=passwordEncoder;
    }

    @GetMapping("/profile")
    public String viewProfile(Model model) {
        if(!model.containsAttribute("nameForm")) {
            model.addAttribute("nameForm",new UpdateNameRequest(requireCurrentProfile().getFullName()));
        }
        if(!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm",new UpdatePasswordRequest());
        }
        return "profile";
    }

    @PostMapping("/profile/name")
    public String updateName(@Valid @ModelAttribute("nameForm")UpdateNameRequest nameForm,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("passwordForm", new UpdatePasswordRequest());
            model.addAttribute("nameError",bindingResult.getFieldError("fullName").getDefaultMessage());
            return "profile";
        }

        Profile profile = requireCurrentProfile();
        User user = userRepository.findByEmailIgnoreCase(profile.getEmail())
                .orElseThrow(() -> new IllegalStateException("Signed-in member has no account row"));
        user.setFullName(nameForm.getFullName().trim());
        userRepository.save(user);

        // Redirect instead of returning the view directly, so the nav bar's
        // "profile" attribute is reloaded from the database on the next
        // request — it's populated before this method runs, so returning the
        // view directly would still show the pre-update name.
        redirectAttributes.addFlashAttribute("nameSuccess", "Name updated.");
        return "redirect:/profile";
    }

    @PostMapping("/profile/password")
    public String updatePassword(@Valid @ModelAttribute("passwordForm") UpdatePasswordRequest passwordForm,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        Profile profile = requireCurrentProfile();
        User user = userRepository.findByEmailIgnoreCase(profile.getEmail())
                .orElseThrow(() -> new IllegalStateException("Signed-in member has no account row"));

        if (!bindingResult.hasErrors()
                && !passwordForm.getNewPassword().equals(passwordForm.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "New passwords do not match");
        }
        if (!bindingResult.hasErrors()
                && !passwordEncoder.matches(passwordForm.getCurrentPassword(), user.getPassword())) {
            bindingResult.rejectValue("currentPassword", "invalid", "Current password is incorrect");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("nameForm", new UpdateNameRequest(profile.getFullName()));
            model.addAttribute("passwordError", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "profile";
        }

        user.setPassword(passwordEncoder.encode(passwordForm.getNewPassword()));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("passwordSuccess", "Password updated.");
        return "redirect:/profile";
    }
}