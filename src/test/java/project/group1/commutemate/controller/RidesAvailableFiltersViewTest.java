package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
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
import project.group1.commutemate.model.RideLocations;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;

/**
 * Issue #26 — the Available Rides departure/destination filters should be the same
 * predefined stop list as the Create Ride form, not free-text boxes.
 */
@WebMvcTest(controllers = RidesController.class)
@AutoConfigureMockMvc(addFilters = false)
class RidesAvailableFiltersViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private RideCoordinationService coordinationService;

    @MockitoBean
    private RideService rideService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void signInRider() {
        when(currentUserService.currentProfile()).thenReturn(Optional.of(
                new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0)));
        when(rideService.search("", "Departure")).thenReturn(List.of());
    }

    @Test
    void departureAndDestinationFiltersAreSelectableLists() throws Exception {
        String html = mockMvc.perform(get("/rides/available"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(html.contains("<select name=\"departure\""), "departure should be a list");
        assertTrue(html.contains("<select name=\"destination\""), "destination should be a list");

        for (String stop : RideLocations.ALL) {
            assertTrue(html.contains("<option value=\"" + stop + "\""), "missing option: " + stop);
        }
    }
}
