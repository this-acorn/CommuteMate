package project.group1.commutemate.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.model.BusArrival;
import project.group1.commutemate.model.CampusDepartures;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.model.ServiceAlert;
import project.group1.commutemate.model.TransitInfo;
import project.group1.commutemate.service.TransitService;

/**
 * Renders the rider dashboard with a stubbed TransitService to verify the US-10
 * outcomes: buses shown, no buses, and API unavailable. The rider dashboard no
 * longer renders service alerts (still shown on the driver dashboard).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardTransitViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransitService transitService;

    @MockitoBean
    private CurrentUserService currentUserService;

    private static final BusArrival TEST_BUS =
            new BusArrival("999", "Test Terminal Alpha", 7, "9:07pm");

    private static final List<CampusDepartures> TEST_CAMPUS =
            List.of(new CampusDepartures("Test Campus Yankee", List.of(TEST_BUS)));

    private static final List<ServiceAlert> TEST_ALERT =
            List.of(new ServiceAlert("Test Alert Bravo", "Detour in effect"));

    @BeforeEach
    void signInAsRider() {
        when(currentUserService.currentProfile())
                .thenReturn(Optional.of(new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0)));
    }

    @Test
    void showsUpcomingBuses() throws Exception {
        when(transitService.getTransitInfo())
                .thenReturn(new TransitInfo(true, TEST_CAMPUS, true, TEST_ALERT));

        mockMvc.perform(get("/dashboard/rider").with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("999")))
                .andExpect(content().string(containsString("Test Terminal Alpha")))
                .andExpect(content().string(containsString("9:07pm")))     // actual departure time
                .andExpect(content().string(containsString("in 7 min")))   // countdown
                .andExpect(content().string(containsString("Test Campus Yankee")))   // campus heading
                .andExpect(content().string(not(containsString("Test Alert Bravo"))));
    }

    /** Buses are grouped under the campus they leave from, so every campus is labelled. */
    @Test
    void groupsBusesUnderEachCampus() throws Exception {
        when(transitService.getTransitInfo()).thenReturn(new TransitInfo(
                true,
                List.of(new CampusDepartures("Test Campus Yankee", List.of(TEST_BUS)),
                        new CampusDepartures("Test Campus Xray",
                                List.of(new BusArrival("888", "Test Terminal Delta", 3, "9:03pm")))),
                true,
                List.of()));

        mockMvc.perform(get("/dashboard/rider").with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Campus Yankee")))
                .andExpect(content().string(containsString("Test Campus Xray")))
                .andExpect(content().string(containsString("888")))
                .andExpect(content().string(containsString("Test Terminal Delta")));
    }

    /**
     * A campus with nothing due keeps its heading and says so — dropping it made the
     * card look broken when SFU Burnaby ran dry between buses.
     */
    @Test
    void keepsACampusHeadingWhenNoBusesAreDueThere() throws Exception {
        when(transitService.getTransitInfo()).thenReturn(new TransitInfo(
                true,
                List.of(new CampusDepartures("Test Campus Yankee", List.of()),
                        new CampusDepartures("Test Campus Xray", List.of(TEST_BUS))),
                true,
                TEST_ALERT));

        mockMvc.perform(get("/dashboard/rider").with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Campus Yankee")))
                .andExpect(content().string(containsString("No buses due right now.")))
                .andExpect(content().string(containsString("Test Campus Xray")))
                .andExpect(content().string(containsString("999")));
    }

    @Test
    void showsErrorWhenApiUnavailable() throws Exception {
        when(transitService.getTransitInfo())
                .thenReturn(new TransitInfo(false, List.of(), false, List.of()));

        mockMvc.perform(get("/dashboard/rider").with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Transit information is temporarily unavailable.")));
    }

}
