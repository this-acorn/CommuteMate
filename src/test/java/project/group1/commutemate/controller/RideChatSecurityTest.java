package project.group1.commutemate.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.exception.RideChatAccessException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideChatView;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.service.RideChatService;

/** Security test coverage for the fetch-based chat endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
class RideChatSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private RideChatService chatService;

    private Profile rider;

    @BeforeEach
    void setUp() {
        rider = new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0);
        when(currentUserService.currentProfile()).thenReturn(Optional.of(rider));
    }

    @Test
    void renderedChatFormContainsCsrfTokenForFetchSubmission() throws Exception {
        Ride ride = new Ride(
                "driver@sfu.ca", "Demo Driver", "DD", "Metrotown", "SFU",
                LocalDateTime.now().plusDays(1), 3, 1, 4, 20, 80,
                "Test car", 5.0, null);
        ride.setId(10L);
        when(chatService.openChat(10L, rider))
                .thenReturn(new RideChatView(ride, List.of(), false));

        mockMvc.perform(get("/rides/{rideId}/chat", 10L)
                        .with(user("rider@sfu.ca").roles("RIDER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void unauthenticatedPollingReturnsUnauthorizedInsteadOfLoginHtml() throws Exception {
        mockMvc.perform(get("/rides/{rideId}/chat/messages", 10L)
                        .header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatService);
    }

    @Test
    void messagePostWithoutCsrfIsRejectedBeforeControllerRuns() throws Exception {
        mockMvc.perform(post("/rides/{rideId}/chat/messages", 10L)
                        .with(user("rider@sfu.ca").roles("RIDER"))
                        .param("message", "Hello"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(chatService);
    }

    @Test
    void authenticatedParticipantCanSendWithCsrf() throws Exception {
        mockMvc.perform(post("/rides/{rideId}/chat/messages", 10L)
                        .with(user("rider@sfu.ca").roles("RIDER"))
                        .with(csrf())
                        .param("message", "I am outside."))
                .andExpect(status().isNoContent());

        verify(chatService).sendMessage(eq(10L), same(rider), eq("I am outside."));
    }

    @Test
    void authenticatedNonParticipantGetsForbiddenInsteadOfRedirect() throws Exception {
        when(chatService.loadMessagesAfter(10L, rider, 0L))
                .thenThrow(new RideChatAccessException(
                        "Only the ride owner and confirmed riders can access this chat."));

        mockMvc.perform(get("/rides/{rideId}/chat/messages", 10L)
                        .with(user("rider@sfu.ca").roles("RIDER"))
                        .header("Accept", "application/json"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(
                        "Only the ride owner and confirmed riders can access this chat."));
    }
}
