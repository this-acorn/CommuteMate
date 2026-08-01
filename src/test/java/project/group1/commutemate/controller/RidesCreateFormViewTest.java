package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
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
import project.group1.commutemate.exception.RideOperationException.ErrorCode;
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
    void invalidCreateRideUsesPrgAndPreservesValuesInFlashAttributes() throws Exception {
        String errorMessage = "Route endpoints cannot be identical.";
        when(rideService.create(anyString(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class), anyInt(), anyInt(), any()))
                .thenThrow(new RideOperationException(ErrorCode.SAME_ROUTE, errorMessage));

        Map<String, String> expectedFieldErrors = new LinkedHashMap<>();
        expectedFieldErrors.put("from", errorMessage);
        expectedFieldErrors.put("to", errorMessage);

        mockMvc.perform(post("/rides/create")
                        .param("from", "Metrotown Station")
                        .param("to", "Metrotown Station")
                        .param("date", "2026-07-18")
                        .param("time", "09:45")
                        .param("seats", "4")
                        .param("price", "7")
                        .param("notes", "Blue car by the entrance"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rides/create"))
                .andExpect(flash().attribute("formFrom", "Metrotown Station"))
                .andExpect(flash().attribute("formTo", "Metrotown Station"))
                .andExpect(flash().attribute("formDate", "2026-07-18"))
                .andExpect(flash().attribute("formTime", "09:45"))
                .andExpect(flash().attribute("formSeats", 4))
                .andExpect(flash().attribute("formPrice", 7))
                .andExpect(flash().attribute("formNotes", "Blue car by the entrance"))
                .andExpect(flash().attribute("errorMessage", errorMessage))
                .andExpect(flash().attribute("fieldErrors", expectedFieldErrors));
    }

    @Test
    void createFormRendersFlashedValuesAndHighlightsRelatedFields() throws Exception {
        String errorMessage = "Route endpoints cannot be identical.";
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put("from", errorMessage);
        fieldErrors.put("to", errorMessage);

        String html = mockMvc.perform(get("/rides/create")
                        .flashAttr("formFrom", "Metrotown Station")
                        .flashAttr("formTo", "Metrotown Station")
                        .flashAttr("formDate", "2026-07-18")
                        .flashAttr("formTime", "09:45")
                        .flashAttr("formSeats", 4)
                        .flashAttr("formPrice", 7)
                        .flashAttr("formNotes", "Blue car by the entrance")
                        .flashAttr("errorMessage", errorMessage)
                        .flashAttr("fieldErrors", fieldErrors))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(html.contains(errorMessage));
        assertTrue(html.contains("value=\"2026-07-18\""));
        assertTrue(html.contains("value=\"09:45\""));
        assertTrue(html.contains("Blue car by the entrance"));
        assertTrue(html.contains("field-error"));
    }
}
