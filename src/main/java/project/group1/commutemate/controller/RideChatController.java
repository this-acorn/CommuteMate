package project.group1.commutemate.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RideChatView;
import project.group1.commutemate.service.RideChatService;

/** Web endpoints for the persistent conversation attached to a ride. */
@Controller
public class RideChatController extends AuthenticatedController {

    private final RideChatService chatService;

    public RideChatController(RideChatService chatService,
                              CurrentUserService currentUserService) {
        super(currentUserService);
        this.chatService = chatService;
    }

    @GetMapping("/rides/{rideId}/chat")
    public String chat(@PathVariable Long rideId,
                       Model model,
                       RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            RideChatView chat = chatService.openChat(rideId, profile);
            model.addAttribute("chat", chat);
            model.addAttribute("maxMessageLength", RideChatService.MAX_MESSAGE_LENGTH);
            return "ride-chat";
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/rides/" + rideId;
        }
    }

    @PostMapping("/rides/{rideId}/chat/messages")
    public String sendMessage(@PathVariable Long rideId,
                              @RequestParam(name = "message", required = false) String message,
                              RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            chatService.sendMessage(rideId, profile, message);
            redirect.addFlashAttribute("successMessage", "Message sent.");
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rides/" + rideId + "/chat";
    }
}
