package project.group1.commutemate.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideRequest;
import project.group1.commutemate.service.NotificationService;
import project.group1.commutemate.service.RideCoordinationService;

/** HTTP actions for ride requests. */
@Controller
public class RideRequestController extends AuthenticatedController {

    private final RideCoordinationService coordinationService;
    private final NotificationService notificationService;

    public RideRequestController(RideCoordinationService coordinationService,
                                 CurrentUserService currentUserService,
                                 NotificationService notificationService) {
        super(currentUserService, notificationService);
        this.coordinationService = coordinationService;
        this.notificationService = notificationService;
    }

    // requests
    @PostMapping("/rides/{rideId}/requests")
    public String requestSeat(@PathVariable Long rideId,
                              RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            RideRequest request = coordinationService.requestSeat(rideId, profile);
            Ride ride = request.getRide();
            notificationService.notify(ride.getDriverEmail(),
                    profile.getFullName() + " requested a seat on your ride to " + ride.getTo() + ".",
                    ride.getId());
            redirect.addFlashAttribute("successMessage",
                    "Seat request sent. The driver must confirm it before a seat is reserved.");
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rides/" + rideId;
    }

    // confirm
    @PostMapping("/ride-requests/{requestId}/confirm")
    public String confirm(@PathVariable Long requestId,
                          RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            RideRequest request = coordinationService.confirmRequest(requestId, profile);
            notificationService.notify(request.getRiderEmail(),
                    "Your seat request for " + request.getRide().getFrom() + " \u2192 "
                            + request.getRide().getTo() + " was confirmed.",
                    request.getRide().getId());
            redirect.addFlashAttribute("successMessage", "Rider confirmed and one seat reserved.");
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/dashboard/driver";
    }

    // reject
    @PostMapping("/ride-requests/{requestId}/reject")
    public String reject(@PathVariable Long requestId,
                         RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            RideRequest request = coordinationService.rejectRequest(requestId, profile);
            notificationService.notify(request.getRiderEmail(),
                    "Your seat request for " + request.getRide().getFrom() + " \u2192 "
                            + request.getRide().getTo() + " was declined.",
                    request.getRide().getId());
            redirect.addFlashAttribute("successMessage", "Ride request rejected.");
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/dashboard/driver";
    }

    // cancel
    @PostMapping("/ride-requests/{requestId}/cancel")
    public String cancel(@PathVariable Long requestId,
                         RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            RideRequest request = coordinationService.cancelRequest(requestId, profile);
            notificationService.notify(request.getRide().getDriverEmail(),
                    profile.getFullName() + " cancelled their request for your ride to "
                            + request.getRide().getTo() + ".",
                    request.getRide().getId());
            redirect.addFlashAttribute("successMessage", "Ride request cancelled.");
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/dashboard/rider";
    }

    // Epic 4: post-ride workflow, step 1 — rider confirms boarding
    @PostMapping("/ride-requests/{requestId}/board")
    public String confirmBoarding(@PathVariable Long requestId,
                                  RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            RideRequest request = coordinationService.confirmBoarding(requestId, profile);
            notificationService.notify(request.getRide().getDriverEmail(),
                    profile.getFullName() + " confirmed boarding for your ride to "
                            + request.getRide().getTo() + ".",
                    request.getRide().getId());
            redirect.addFlashAttribute("successMessage", "Boarding confirmed. Have a good ride!");
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/dashboard/rider";
    }

    // Epic 4: post-ride workflow, step 2 — driver confirms arrival, which
    // completes the ride for everyone who boarded and credits rewards
    @PostMapping("/rides/{rideId}/arrived")
    public String confirmArrival(@PathVariable Long rideId,
                                 RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            List<RideRequest> completed = coordinationService.confirmArrival(rideId, profile);
            for (RideRequest request : completed) {
                notificationService.notify(request.getRiderEmail(),
                        "Your ride to " + request.getRide().getTo()
                                + " is complete. Thanks for riding with CommuteMate!",
                        rideId);
            }
            redirect.addFlashAttribute("successMessage", "Ride completed. Rewards credited.");
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/dashboard/driver";
    }
}