package project.group1.commutemate.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.service.NotificationService;
import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;

/**
 * US-12 (browse available rides, including the empty state) and US-13 (the
 * A → B route preview on each ride card), asserted against the rendered
 * rides-available page rather than just the model.
 */
@WebMvcTest(controllers = RidesController.class)
@AutoConfigureMockMvc(addFilters = false)
class RidesAvailableListViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RideService rideService;

    @MockitoBean
    private RideCoordinationService coordinationService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void signInAsRider() {
        when(currentUserService.currentProfile()).thenReturn(
                Optional.of(new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0)));
    }

    // ---------- US-12: browse available rides ----------

    @Test
    void availableRidesShowEachRidesDetails() throws Exception {
        when(rideService.recommended(any(), any(), any(), any()))
                .thenReturn(List.of(ride()));

        mockMvc.perform(get("/rides/available"))
                .andExpect(status().isOk())
                .andExpect(view().name("rides-available"))
                .andExpect(model().attributeExists("rides"))
                .andExpect(content().string(containsString("Demo Driver")))
                .andExpect(content().string(containsString("Metrotown Station")))
                .andExpect(content().string(containsString("SFU Burnaby — AQ")))
                .andExpect(content().string(containsString("8:15 AM")));
    }

    /**
     * The empty-state panel is always in the markup and hidden with an inline
     * style, so "is it shown" means "is display:none absent", not "is the text
     * present".
     */
    @Test
    void emptyRideListShowsTheNoRidesMessage() throws Exception {
        when(rideService.recommended(any(), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/rides/available"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rides", List.of()))
                .andExpect(content().string(containsString("No rides are currently available.")))
                .andExpect(content().string(not(containsString("display:none"))));
    }

    @Test
    void noRidesMessageIsHiddenWhenRidesExist() throws Exception {
        when(rideService.recommended(any(), any(), any(), any()))
                .thenReturn(List.of(ride()));

        mockMvc.perform(get("/rides/available"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("display:none")));
    }

    // ---------- US-13: basic route preview ----------

    @Test
    void eachRideCardRendersARoutePreviewFromPickupToDestination() throws Exception {
        when(rideService.recommended(any(), any(), any(), any()))
                .thenReturn(List.of(ride()));

        mockMvc.perform(get("/rides/available"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Route Preview")))
                // the A -> B layout: pickup, arrow, destination
                .andExpect(content().string(containsString("Metrotown Station")))
                .andExpect(content().string(containsString("→")))
                .andExpect(content().string(containsString("SFU Burnaby — AQ")));
    }

    private Ride ride() {
        Ride ride = new Ride("driver@sfu.ca", "Demo Driver", "DD",
                "Metrotown Station", "SFU Burnaby — AQ",
                LocalDateTime.of(2026, 9, 1, 8, 15), 3, 0, 4, 25, 82,
                "Test car", 4.8, null);
        ride.setId(10L);
        return ride;
    }
}
