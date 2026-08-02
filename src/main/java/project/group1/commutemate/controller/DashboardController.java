package project.group1.commutemate.controller;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideRequest;
import project.group1.commutemate.service.NotificationService;
import project.group1.commutemate.service.RideChatService;
import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;
import project.group1.commutemate.service.TransitService;
import project.group1.commutemate.service.WeatherService;

/** Rider and driver dashboards. */
@Controller
public class DashboardController extends AuthenticatedController {

    private final RideService rideService;
    private final RideCoordinationService coordinationService;
    private final RideChatService chatService;
    private final Clock clock;
    private final TransitService transitService;
    private final WeatherService weatherService;

    public DashboardController(RideService rideService,
                               RideCoordinationService coordinationService,
                               RideChatService chatService,
                               CurrentUserService currentUserService,
                               NotificationService notificationService,
                               Clock clock,
                               TransitService transitService,
                               WeatherService weatherService) {
        super(currentUserService, notificationService);
        this.rideService = rideService;
        this.coordinationService = coordinationService;
        this.chatService = chatService;
        this.clock = clock;
        this.transitService = transitService;
        this.weatherService = weatherService;
    }

    // rider
    @GetMapping("/dashboard/rider")
    public String riderDashboard(Model model) {
        Profile profile = requireCurrentProfile();
        LocalDateTime now = LocalDateTime.now(clock);
        List<RideRequest> requests = coordinationService.findRequestsForRider(profile.getEmail());
        // Includes BOARDING_CONFIRMED so the ride stays visible after boarding.
        Ride nextConfirmedRide = requests.stream()
                .filter(request -> request.getStatus() == RequestStatus.CONFIRMED
                        || request.getStatus() == RequestStatus.BOARDING_CONFIRMED)
                .map(RideRequest::getRide)
                .filter(ride -> ride.getDepartAt() != null && ride.getDepartAt().isAfter(now))
                .min((first, second) -> first.getDepartAt().compareTo(second.getDepartAt()))
                .orElse(null);

        List<Ride> upcoming = rideService.findAllUpcoming();
        List<Ride> suggested = upcoming.stream()
                .filter(ride -> !ride.isFull())
                .filter(ride -> !ride.getDriverEmail().equalsIgnoreCase(profile.getEmail()))
                .limit(2)
                .toList();

        model.addAttribute("now", now);
        model.addAttribute("nextRide", nextConfirmedRide);
        model.addAttribute("riderRequests", requests);
        model.addAttribute("suggested", suggested);
        model.addAttribute("unreadChatRideIds",
                chatService.findUnreadRideIds(profile, chatRideIdsForRider(requests)));
        model.addAttribute("availableRideCount",
                upcoming.stream().filter(ride -> !ride.isFull()).count());
        model.addAttribute("transit", transitService.getTransitInfo());
        model.addAttribute("weather", weatherService.getCurrentWeather().orElse(null));
        return "dashboard-rider";
    }

    // driver
    @GetMapping("/dashboard/driver")
    public String driverDashboard(Model model) {
        Profile profile = requireCurrentProfile();
        LocalDateTime now = LocalDateTime.now(clock);
        List<Ride> myRides = new java.util.ArrayList<>(
                rideService.findUpcomingByDriverEmail(profile.getEmail()));
        // Epic 4: a ride may have already departed by the time the driver
        // needs to click "Arrived", so surface those too, even though
        // they're no longer "upcoming".
        for (Ride awaitingArrival : coordinationService.findRidesAwaitingArrival(profile.getEmail())) {
            if (myRides.stream().noneMatch(r -> r.getId().equals(awaitingArrival.getId()))) {
                myRides.add(awaitingArrival);
            }
        }
        List<RideRequest> requests = coordinationService.findRequestsForDriver(profile.getEmail());

        model.addAttribute("now", now);
        model.addAttribute("myRides", myRides);
        model.addAttribute("driverRequests", requests);
        model.addAttribute("unreadChatRideIds",
                chatService.findUnreadRideIds(profile, chatRideIdsForDriver(myRides, requests)));
        model.addAttribute("upcomingRideCount", myRides.size());
        // Issue #25: driver dashboard previously showed hardcoded points/eco-score.
        model.addAttribute("profile", profile);
        model.addAttribute("confirmedRiderCount", requests.stream()
                .filter(request -> request.getStatus() == RequestStatus.CONFIRMED)
                .count());
        model.addAttribute("weather", weatherService.getCurrentWeather().orElse(null));
        return "dashboard-driver";
    }


    private Set<Long> chatRideIdsForRider(List<RideRequest> requests) {
        Set<Long> rideIds = new LinkedHashSet<>();
        for (RideRequest request : requests) {
            if (request.getRide() != null && request.getRide().getId() != null
                    && isChatStatus(request.getStatus())) {
                rideIds.add(request.getRide().getId());
            }
        }
        return rideIds;
    }

    private Set<Long> chatRideIdsForDriver(List<Ride> rides, List<RideRequest> requests) {
        Set<Long> rideIds = new LinkedHashSet<>();
        for (Ride ride : rides) {
            if (ride.getId() != null) {
                rideIds.add(ride.getId());
            }
        }
        for (RideRequest request : requests) {
            if (request.getRide() != null && request.getRide().getId() != null
                    && isChatStatus(request.getStatus())) {
                rideIds.add(request.getRide().getId());
            }
        }
        return rideIds;
    }

    private boolean isChatStatus(RequestStatus status) {
        return status == RequestStatus.CONFIRMED
                || status == RequestStatus.BOARDING_CONFIRMED
                || status == RequestStatus.COMPLETED;
    }
}
