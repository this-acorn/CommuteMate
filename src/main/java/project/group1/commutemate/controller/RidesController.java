package project.group1.commutemate.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.LocationCoordinates;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideLocations;
import project.group1.commutemate.service.NotificationService;
import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;

/** Browse, inspect, and publish persistent rides. */
@Controller
public class RidesController extends AuthenticatedController {

    private static final String[] SORT_OPTIONS = {"Departure", "Price", "Eco-Score", "Rating"};

    private final RideService rideService;
    private final RideCoordinationService coordinationService;
    private final Clock clock;

    public RidesController(RideService rideService,
                           RideCoordinationService coordinationService,
                           CurrentUserService currentUserService,
                           NotificationService notificationService,
                           Clock clock) {
        super(currentUserService, notificationService);
        this.rideService = rideService;
        this.coordinationService = coordinationService;
        this.clock = clock;
    }

    // available rides
    @GetMapping("/rides/available")
        public String available(@RequestParam(name = "q", required = false, defaultValue = "") String query,
                        @RequestParam(name = "departure", required = false) String departure,
                        @RequestParam(name = "destination", required = false) String destination,
                        @RequestParam(required = false, defaultValue = "Departure") String sort,
                        Model model) {

                      List<Ride> rides = rideService.search(query, sort);

    if (hasText(departure)) {
        rides = rides.stream()
                .filter(ride -> containsIgnoreCase(ride.getFrom(), departure))
                .toList();
    }

    if (hasText(destination)) {
        rides = rides.stream()
                .filter(ride -> containsIgnoreCase(ride.getTo(), destination))
                .toList();
    }

    model.addAttribute("rides", rides);
    model.addAttribute("query", query == null ? "" : query);
    model.addAttribute("departure", departure == null ? "" : departure);
    model.addAttribute("destination", destination == null ? "" : destination);
    model.addAttribute("sort", sort);
    model.addAttribute("sortOptions", SORT_OPTIONS);
    model.addAttribute("communityStops", RideLocations.COMMUNITY_STOPS);
    model.addAttribute("campusStops", RideLocations.CAMPUS_STOPS);
    return "rides-available";
}
        private boolean hasText(String value) {
    return value != null && !value.isBlank();
}

    private boolean containsIgnoreCase(String text, String search) {
    if (text == null || search == null) {
        return false;
    }
    return text.toLowerCase().contains(search.toLowerCase());
}

    //  ride details
    @GetMapping("/rides/{rideId}")
    public String details(@PathVariable Long rideId,
                          Model model,
                          RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            Ride ride = rideService.findById(rideId);
            model.addAttribute("ride", ride);
            model.addAttribute("myRequest",
                    coordinationService.findRequestForRider(rideId, profile.getEmail()).orElse(null));
            model.addAttribute("owner", ride.getDriverEmail().equalsIgnoreCase(profile.getEmail()));
            model.addAttribute("upcoming",
                    ride.getDepartAt() != null && ride.getDepartAt().isAfter(LocalDateTime.now(clock)));

            // Epic 3: interactive map — plot pins when both endpoints match a
            // known SFU-area location. Missing/unmatched locations simply
            // don't get a map (handled in the template), the rest of the
            // page is unaffected.
            LocationCoordinates.lookup(ride.getFrom()).ifPresent(coords -> model.addAttribute("pickupCoords", coords));
            LocationCoordinates.lookup(ride.getTo()).ifPresent(coords -> model.addAttribute("destinationCoords", coords));

            return "ride-details";
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/rides/available";
        }
    }

    // create rides
    @GetMapping("/rides/create")
    public String createForm(Model model) {
        populateCreateForm(model);
        return "rides-create";
    }

    @PostMapping("/rides/create")
    public String create(@RequestParam String from,
                         @RequestParam String to,
                         @RequestParam String date,
                         @RequestParam String time,
                         @RequestParam(defaultValue = "3") int seats,
                         @RequestParam(defaultValue = "4") int price,
                         @RequestParam(required = false) String notes,
                         RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            LocalDateTime departAt = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time));
            rideService.create(profile.getEmail(), profile.getFullName(), from, to,
                    departAt, seats, price, notes);
            redirect.addFlashAttribute("successMessage", "Ride published successfully.");
            return "redirect:/dashboard/driver";
        } catch (DateTimeParseException ex) {
            return redirectCreateFormWithError(redirect, from, to, date, time, seats, price, notes,
                    "Enter a valid departure date and time.", "date", "time");
        } catch (RideOperationException ex) {
            return redirectCreateFormWithError(redirect, from, to, date, time, seats, price, notes,
                    ex.getMessage(), fieldsForCreateError(ex));
        }
    }

    private void populateCreateForm(Model model) {
        String minimumDate = LocalDate.now(clock).toString();
        model.addAttribute("minimumDate", minimumDate);
        model.addAttribute("communityStops", RideLocations.COMMUNITY_STOPS);
        model.addAttribute("campusStops", RideLocations.CAMPUS_STOPS);
        model.addAttribute("defaultPickup", RideLocations.DEFAULT_PICKUP);
        model.addAttribute("defaultDestination", RideLocations.DEFAULT_DESTINATION);

        if (!model.containsAttribute("formFrom")) {
            model.addAttribute("formFrom", RideLocations.DEFAULT_PICKUP);
            model.addAttribute("formTo", RideLocations.DEFAULT_DESTINATION);
            model.addAttribute("formDate", minimumDate);
            model.addAttribute("formTime", "08:15");
            model.addAttribute("formSeats", 3);
            model.addAttribute("formPrice", 4);
            model.addAttribute("formNotes", "");
        }
    }

    private String redirectCreateFormWithError(RedirectAttributes redirect,
                                               String from,
                                               String to,
                                               String date,
                                               String time,
                                               int seats,
                                               int price,
                                               String notes,
                                               String message,
                                               String... fields) {
        redirect.addFlashAttribute("formFrom", from);
        redirect.addFlashAttribute("formTo", to);
        redirect.addFlashAttribute("formDate", date);
        redirect.addFlashAttribute("formTime", time);
        redirect.addFlashAttribute("formSeats", seats);
        redirect.addFlashAttribute("formPrice", price);
        redirect.addFlashAttribute("formNotes", notes == null ? "" : notes);
        redirect.addFlashAttribute("errorMessage", message);

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (String field : fields) {
            fieldErrors.put(field, message);
        }
        redirect.addFlashAttribute("fieldErrors", fieldErrors);
        return "redirect:/rides/create";
    }

    private String[] fieldsForCreateError(RideOperationException exception) {
        return switch (exception.getErrorCode()) {
            case LOCATION_REQUIRED, SAME_ROUTE -> new String[]{"from", "to"};
            case DEPARTURE_INVALID -> new String[]{"date", "time"};
            case SEATS_INVALID -> new String[]{"seats"};
            case PRICE_INVALID -> new String[]{"price"};
            default -> new String[0];
        };
    }

    // delete rides
    @PostMapping("/rides/{rideId}/delete")
    public String deleteRide(@PathVariable Long rideId,
                             RedirectAttributes redirect) {
        Profile profile = requireCurrentProfile();
        try {
            coordinationService.deleteOwnedRide(rideId, profile);
            redirect.addFlashAttribute("successMessage", "Ride and all of its requests were deleted.");
        } catch (RideOperationException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/dashboard/driver";
    }
}