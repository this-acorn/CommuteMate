package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;
import project.group1.commutemate.model.Ride;

@WebMvcTest(controllers = {RideRequestController.class, RidesController.class})
@AutoConfigureMockMvc(addFilters = false)
class RideAuthorizationControllerTest {

    private static final String SIGNED_IN_EMAIL = "owner@sfu.ca";
    private static final String FORGED_EMAIL = "victim@sfu.ca";

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

    private Profile signedInProfile;

    @BeforeEach
    void setUp() {
        signedInProfile = new Profile(
                SIGNED_IN_EMAIL, "Signed In Member", Role.RIDER, 0, 0);
        when(currentUserService.currentProfile()).thenReturn(Optional.of(signedInProfile));
    }

    @Test
    void confirmUsesSignedInProfileWhenEmailParameterIsForged() throws Exception {
        mockMvc.perform(post("/ride-requests/{requestId}/confirm", 41L)
                        .param("email", FORGED_EMAIL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/driver"));

        assertEquals(SIGNED_IN_EMAIL, signedInProfile.getEmail());
        verify(coordinationService).confirmRequest(eq(41L), same(signedInProfile));
    }

    @Test
    void rejectUsesSignedInProfileWhenEmailParameterIsForged() throws Exception {
        mockMvc.perform(post("/ride-requests/{requestId}/reject", 42L)
                        .param("email", FORGED_EMAIL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/driver"));

        assertEquals(SIGNED_IN_EMAIL, signedInProfile.getEmail());
        verify(coordinationService).rejectRequest(eq(42L), same(signedInProfile));
    }

    @Test
    void cancelUsesSignedInProfileWhenEmailParameterIsForged() throws Exception {
        mockMvc.perform(post("/ride-requests/{requestId}/cancel", 43L)
                        .param("email", FORGED_EMAIL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/rider"));

        assertEquals(SIGNED_IN_EMAIL, signedInProfile.getEmail());
        verify(coordinationService).cancelRequest(eq(43L), same(signedInProfile));
    }

    @Test
    void deleteRideUsesSignedInProfileWhenEmailParameterIsForged() throws Exception {
        mockMvc.perform(post("/rides/{rideId}/delete", 44L)
                        .param("email", FORGED_EMAIL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/driver"));

        assertEquals(SIGNED_IN_EMAIL, signedInProfile.getEmail());
        verify(coordinationService).deleteOwnedRide(eq(44L), same(signedInProfile));
    }
        @Test
        void availableRidesAppliesDepartureAndDestinationFilters() throws Exception {
         Ride metrotownRide = new Ride(
            "driver@sfu.ca",
            "Demo Driver",
            "DD",
            "Metrotown Station",
            "SFU Burnaby — AQ",
            LocalDateTime.now().plusHours(1),
            3,
            0,
            4,
            25,
            79,
            "Test Car",
            5.0,
            null
         );

         Ride coquitlamRide = new Ride(
            "driver2@sfu.ca",
            "Other Driver",
            "OD",
            "Coquitlam Central",
            "SFU Burnaby — Convocation Mall",
            LocalDateTime.now().plusHours(1),
            3,
            0,
            4,
            35,
            68,
            "Test Car",
            4.7,
            null
          );

         when(rideService.search("", "Departure"))
            .thenReturn(List.of(metrotownRide, coquitlamRide));

         mockMvc.perform(get("/rides/available")
                    .param("departure", "Metrotown")
                    .param("destination", "SFU"))
            .andExpect(status().isOk())
            .andExpect(view().name("rides-available"))
            .andExpect(model().attribute("departure", "Metrotown"))
            .andExpect(model().attribute("destination", "SFU"))
            .andExpect(model().attribute("sort", "Departure"))
            .andExpect(model().attribute("rides", List.of(metrotownRide)));

          verify(rideService).search("", "Departure");
}

}
