package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
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
}
