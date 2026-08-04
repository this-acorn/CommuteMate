package project.group1.commutemate.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideRequest;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.service.NotificationService;
import project.group1.commutemate.service.RideCoordinationService;

/**
 * US-18 "Request a Seat" at the HTTP layer: the acceptance tests require that a
 * success or error message reaches the rider, which is a controller concern.
 * The seat-count and status rules themselves live in RideCoordinationServiceTest.
 */
@WebMvcTest(controllers = RideRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class RequestSeatControllerTest {

    private static final long RIDE_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private RideCoordinationService coordinationService;

    // The controller notifies the driver on a successful request, and
    // AuthenticatedController reads the unread-notification count for the nav bar.
    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private Clock clock;

    private Profile rider;

    @BeforeEach
    void setUp() {
        rider = new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0);
        when(currentUserService.currentProfile()).thenReturn(Optional.of(rider));
    }

    @Test
    void validRequestShowsSuccessMessageOnTheRideDetailsPage() throws Exception {
        Ride ride = upcomingRideWithFreeSeats();
        when(coordinationService.requestSeat(eq(RIDE_ID), same(rider)))
                .thenReturn(new RideRequest(ride, rider.getEmail(), rider.getFullName()));

        mockMvc.perform(post("/rides/{rideId}/requests", RIDE_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rides/" + RIDE_ID))
                .andExpect(flash().attribute("successMessage",
                        "Seat request sent. The driver must confirm it before a seat is reserved."))
                .andExpect(flash().attributeCount(1));

        verify(coordinationService).requestSeat(RIDE_ID, rider);
    }

    @Test
    void duplicateRequestShowsErrorMessage() throws Exception {
        expectRejection("You already requested this ride.");
    }

    @Test
    void ownRideRequestShowsErrorMessage() throws Exception {
        expectRejection("You cannot request your own ride.");
    }

    @Test
    void fullRideRequestShowsErrorMessage() throws Exception {
        expectRejection("This ride is full.");
    }

    @Test
    void departedRideRequestShowsErrorMessage() throws Exception {
        expectRejection("This ride has already departed.");
    }

    /** A rejected request surfaces the rule's message and never a success message. */
    private void expectRejection(String message) throws Exception {
        when(coordinationService.requestSeat(eq(RIDE_ID), same(rider)))
                .thenThrow(new RideOperationException(message));

        mockMvc.perform(post("/rides/{rideId}/requests", RIDE_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rides/" + RIDE_ID))
                .andExpect(flash().attribute("errorMessage", message))
                .andExpect(flash().attributeCount(1));
    }

    private Ride upcomingRideWithFreeSeats() {
        Ride ride = new Ride("driver@sfu.ca", "Demo Driver", "DD", "Metrotown", "SFU",
                LocalDateTime.now().plusDays(1), 3, 0, 4, 20, 80,
                "Test car", 5.0, null);
        ride.setId(RIDE_ID);
        return ride;
    }
}
