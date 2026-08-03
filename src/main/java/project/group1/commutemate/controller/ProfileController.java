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
import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;

@Controller
public class ProfileController extends AuthenticatedController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RideService rideService;
    private final RideCoordinationService rideCoordinationService;

    public ProfileController(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              CurrentUserService currentUserService,
                              NotificationService notificationService,
                              RideService rideService,
                              RideCoordinationService rideCoordinationService) {
        super(currentUserService, notificationService);
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rideService = rideService;
        this.rideCoordinationService = rideCoordinationService;
    }

    @GetMapping("/profile")
    public String viewProfile(Model model) {
        if (!model.containsAttribute("nameForm")) {
            model.addAttribute("nameForm", new UpdateNameRequest(requireCurrentProfile().getFullName()));
        }
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new UpdatePasswordRequest());
        }
        return "profile";
    }

    @PostMapping("/profile/name")
    public String updateName(@Valid @ModelAttribute("nameForm") UpdateNameRequest nameForm,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("passwordForm", new UpdatePasswordRequest());
            model.addAttribute("nameError", bindingResult.getFieldError("fullName").getDefaultMessage());
            return "profile";
        }

        Profile profile = requireCurrentProfile();
        User user = userRepository.findByEmailIgnoreCase(profile.getEmail())
                .orElseThrow(() -> new IllegalStateException("Signed-in member has no account row"));
        String newFullName = nameForm.getFullName().trim();
        user.setFullName(newFullName);
        userRepository.save(user);

        // Keep denormalized display names in sync — a driver's past/current
        // rides and a rider's past/current requests each carry a copy of the
        // name taken at creation time. Whichever role this user isn't will
        // simply have nothing to update.
        rideService.renameDriver(profile.getEmail(), newFullName);
        rideCoordinationService.renameRider(profile.getEmail(), newFullName);

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