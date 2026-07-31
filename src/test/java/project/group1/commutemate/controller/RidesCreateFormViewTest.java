package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RideLocations;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;

@WebMvcTest(controllers = RidesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RidesCreateFormViewTest.FixedClockConfig.class)
class RidesCreateFormViewTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-16T19:00:00Z"), ZoneId.of("America/Vancouver"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private RideCoordinationService coordinationService;

    @MockitoBean
    private RideService rideService;

    @MockitoBean
    private project.group1.commutemate.service.NotificationService notificationService;

    @BeforeEach
    void signInDriver() {
        when(currentUserService.currentProfile()).thenReturn(Optional.of(
                new Profile("driver@sfu.ca", "Demo Driver", Role.DRIVER, 0, 0)));
    }

    @Test
    void createFormRendersPickupAndDestinationAsSelectableLists() throws Exception {
        String html = mockMvc.perform(get("/rides/create"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(html.contains("<select id=\"from\" name=\"from\""), "pickup should be a list");
        assertTrue(html.contains("<select id=\"to\" name=\"to\""), "destination should be a list");

        for (String stop : RideLocations.ALL) {
            assertTrue(html.contains("<option value=\"" + stop + "\""), "missing option: " + stop);
        }
    }
    @Test
    void invalidCreateRideKeepsValuesAndHighlightsRelatedFields() throws Exception {
        when(rideService.create(anyString(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class), anyInt(), anyInt(), any()))
                .thenThrow(new RideOperationException("Pickup and destination must be different."));

        String html = mockMvc.perform(post("/rides/create")
                        .param("from", "Metrotown Station")
                        .param("to", "Metrotown Station")
                        .param("date", "2026-07-18")
                        .param("time", "09:45")
                        .param("seats", "4")
                        .param("price", "7")
                        .param("notes", "Blue car by the entrance"))
                .andExpect(status().isOk())
                .andExpect(view().name("rides-create"))
                .andExpect(model().attribute("formFrom", "Metrotown Station"))
                .andExpect(model().attribute("formTo", "Metrotown Station"))
                .andExpect(model().attribute("formDate", "2026-07-18"))
                .andExpect(model().attribute("formTime", "09:45"))
                .andExpect(model().attribute("formSeats", 4))
                .andExpect(model().attribute("formPrice", 7))
                .andExpect(model().attribute("formNotes", "Blue car by the entrance"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(html.contains("Pickup and destination must be different."));
        assertTrue(html.contains("value=\"2026-07-18\""));
        assertTrue(html.contains("value=\"09:45\""));
        assertTrue(html.contains("Blue car by the entrance"));
        assertTrue(html.contains("field-error"));
    }

}
