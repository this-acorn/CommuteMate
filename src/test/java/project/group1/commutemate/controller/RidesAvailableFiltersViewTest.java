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
import project.group1.commutemate.service.NotificationService;
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
    private NotificationService notificationService;

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

        String departureList = selectNamed(html, "departure");
        String destinationList = selectNamed(html, "destination");

        for (String stop : RideLocations.ALL) {
            String option = "<option value=\"" + stop + "\"";
            assertTrue(departureList.contains(option), "departure is missing option: " + stop);
            assertTrue(destinationList.contains(option), "destination is missing option: " + stop);
        }
    }

    /**
     * The markup of one dropdown only. Searching the whole page instead would let a
     * dropdown that lost its options pass on the options of the other one.
     */
    private static String selectNamed(String html, String name) {
        int start = html.indexOf("<select name=\"" + name + "\"");
        assertTrue(start >= 0, name + " should be a list");
        int end = html.indexOf("</select>", start);
        assertTrue(end > start, name + " list should be closed");
        return html.substring(start, end);
    }
}
