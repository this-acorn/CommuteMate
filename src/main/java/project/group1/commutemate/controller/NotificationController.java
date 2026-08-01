package project.group1.commutemate.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletResponse;
import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.model.Notification;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.service.NotificationService;
import project.group1.commutemate.service.RideService;

// "all notifications" page reachable from the bell icon.
@Controller
public class NotificationController extends AuthenticatedController {

    private final NotificationService notificationService;
    private final RideService rideService;

    public NotificationController(CurrentUserService currentUserService,
                                  NotificationService notificationService,
                                  RideService rideService) {
        super(currentUserService, notificationService);
        this.notificationService = notificationService;
        this.rideService = rideService;
    }

    @GetMapping("/notifications")
    public String list(Model model, HttpServletResponse response) {
        // Prevents the browser from showing a stale unread count via back/forward cache.
        response.setHeader("Cache-Control", "no-store");
        Profile profile = requireCurrentProfile();
        List<Notification> notifications = notificationService.findForUser(profile.getEmail());
        model.addAttribute("notifications", notifications);

        // A notification can outlive the ride it points to (the driver
        // deleted it). Rather than let "View ride" redirect away with a
        // generic error, tell the member upfront in the list itself.
        Set<Long> closedRideIds = notifications.stream()
                .map(Notification::getRelatedRideId)
                .filter(id -> id != null && !rideService.exists(id))
                .collect(Collectors.toSet());
        model.addAttribute("closedRideIds", closedRideIds);
        return "notifications";
    }

    // Marks everything read once the member opens the list — see
    // NotificationService.markAllRead() for why this is all-or-nothing.
    @PostMapping("/notifications/read")
    public String markRead() {
        Profile profile = requireCurrentProfile();
        notificationService.markAllRead(profile.getEmail());
        return "redirect:/notifications";
    }

    // "View ride" from a single notification: marks just that one read,
    // then sends the member to the ride — so coming back to /notifications
    // shows it (and the bell's count) already updated, instead of needing
    // a separate "mark all as read" click.
    @PostMapping("/notifications/{notificationId}/view")
    public String viewRide(@PathVariable Long notificationId) {
        Profile profile = requireCurrentProfile();
        Notification notification = notificationService
                .markRead(notificationId, profile.getEmail())
                .orElse(null);
        if (notification == null || notification.getRelatedRideId() == null) {
            return "redirect:/notifications";
        }
        return "redirect:/rides/" + notification.getRelatedRideId();
    }
}