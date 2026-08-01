package project.group1.commutemate.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.exception.RideChatAccessException;
import project.group1.commutemate.exception.RideChatNotFoundException;
import project.group1.commutemate.exception.RideChatValidationException;
import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RideChatView;
import project.group1.commutemate.model.RideMessageView;
import project.group1.commutemate.service.NotificationService;
import project.group1.commutemate.service.RideChatService;

/** Web endpoints for the persistent conversation attached to a ride. */
@Controller
public class RideChatController extends AuthenticatedController {

    private final RideChatService chatService;

    public RideChatController(RideChatService chatService,
                              CurrentUserService currentUserService,
                              NotificationService notificationService) {
        super(currentUserService, notificationService);
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

    /** Supplies new messages for lightweight browser polling. */
    @GetMapping("/rides/{rideId}/chat/messages")
    @ResponseBody
    public ResponseEntity<?> newMessages(
            @PathVariable Long rideId,
            @RequestParam(name = "after", defaultValue = "0") Long afterId) {
        Profile profile = requireCurrentProfile();
        try {
            List<RideMessageView> messages =
                    chatService.loadMessagesAfter(rideId, profile, afterId);
            return ResponseEntity.ok(messages);
        } catch (RideChatNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (RideChatAccessException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
    }

    /** Saves a message without reloading the whole chat page. */
    @PostMapping("/rides/{rideId}/chat/messages")
    @ResponseBody
    public ResponseEntity<?> sendMessage(
            @PathVariable Long rideId,
            @RequestParam(name = "message", required = false) String message) {
        Profile profile = requireCurrentProfile();
        try {
            chatService.sendMessage(rideId, profile, message);
            return ResponseEntity.noContent().build();
        } catch (RideChatValidationException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (RideChatNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (RideChatAccessException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
    }
}
