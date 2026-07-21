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
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.model.ServiceAlert;
import project.group1.commutemate.model.TransitInfo;
import project.group1.commutemate.service.TransitService;

/**
 * Renders the rider dashboard with a stubbed TransitService to verify the US-10
 * outcomes: buses + alerts shown, no buses, no alerts, alerts feed unavailable,
 * and API unavailable.
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

    @BeforeEach
    void signInAsRider() {
        when(currentUserService.currentProfile())
                .thenReturn(Optional.of(new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0)));
    }

    @Test
    void showsUpcomingBusesAndActiveAlerts() throws Exception {
        when(transitService.getTransitInfo()).thenReturn(new TransitInfo(
                "55555",
                "Test Exchange Zulu",
                true,
                List.of(new BusArrival("999", "Test Terminal Alpha", 7, "9:07pm")),
                true,
                List.of(new ServiceAlert("Test Alert Bravo", "Detour in effect"))));

        mockMvc.perform(get("/dashboard/rider").with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("999")))
                .andExpect(content().string(containsString("Test Terminal Alpha")))
                .andExpect(content().string(containsString("9:07pm")))       // actual arrival time
                .andExpect(content().string(containsString("in 7 min")))     // countdown
                .andExpect(content().string(containsString("Test Exchange Zulu")))  // stop name
                .andExpect(content().string(containsString("Stop #55555")))  // stop number
                .andExpect(content().string(containsString("Test Alert Bravo")));
    }

    @Test
    void showsMessageWhenNoUpcomingBuses() throws Exception {
        when(transitService.getTransitInfo()).thenReturn(new TransitInfo(
                "61935",
                "",
                true,
                List.of(),
                true,
                List.of(new ServiceAlert("Test Alert Bravo", "Detour in effect"))));

        mockMvc.perform(get("/dashboard/rider").with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No upcoming transit information available.")));
    }

    @Test
    void showsMessageWhenNoActiveAlerts() throws Exception {
        when(transitService.getTransitInfo()).thenReturn(new TransitInfo(
                "61935",
                "",
                true,
                List.of(new BusArrival("999", "Test Terminal Alpha", 7, "9:07pm")),
                true,
                List.of()));

        mockMvc.perform(get("/dashboard/rider").with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No active service alerts.")));
    }

    /** A failed alerts feed must not be reported as "no active alerts". */
    @Test
    void showsErrorWhenAlertsFeedUnavailable() throws Exception {
        when(transitService.getTransitInfo()).thenReturn(new TransitInfo(
                "61935",
                "",
                true,
                List.of(new BusArrival("999", "Test Terminal Alpha", 7, "9:07pm")),
                false,
                List.of()));

        mockMvc.perform(get("/dashboard/rider").with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Service alerts are temporarily unavailable.")))
                .andExpect(content().string(not(containsString("No active service alerts."))));
    }

    @Test
    void showsErrorWhenApiUnavailable() throws Exception {
        when(transitService.getTransitInfo()).thenReturn(new TransitInfo("61935", "", false, List.of(), false, List.of()));

        mockMvc.perform(get("/dashboard/rider").with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Transit information is temporarily unavailable.")));
    }
}
