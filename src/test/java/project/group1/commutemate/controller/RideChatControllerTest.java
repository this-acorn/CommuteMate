package project.group1.commutemate.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
import project.group1.commutemate.exception.RideChatAccessException;
import project.group1.commutemate.exception.RideChatNotFoundException;
import project.group1.commutemate.exception.RideChatValidationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideChatView;
import project.group1.commutemate.model.RideMessageView;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.service.RideChatService;

@WebMvcTest(controllers = RideChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class RideChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private RideChatService chatService;

    private Profile profile;
    private Ride ride;

    @BeforeEach
    void setUp() {
        profile = new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0);
        ride = new Ride("driver@sfu.ca", "Demo Driver", "DD", "Metrotown", "SFU",
                LocalDateTime.now().plusDays(1), 3, 1, 4, 20, 80,
                "Test car", 5.0, null);
        ride.setId(10L);
        when(currentUserService.currentProfile()).thenReturn(Optional.of(profile));
    }

    @Test
    void chatPageLoadsAuthorizedConversation() throws Exception {
        RideChatView chat = new RideChatView(ride, List.of(), false);
        when(chatService.openChat(10L, profile)).thenReturn(chat);

        mockMvc.perform(get("/rides/{rideId}/chat", 10L))
                .andExpect(status().isOk())
                .andExpect(view().name("ride-chat"))
                .andExpect(model().attribute("chat", chat))
                .andExpect(model().attribute(
                        "maxMessageLength", RideChatService.MAX_MESSAGE_LENGTH));
    }

    @Test
    void ownerChatPageIdentifiesMemberAsDriver() throws Exception {
        Profile driver = new Profile(
                "driver@sfu.ca", "Demo Driver", Role.DRIVER, 0, 0);
        when(currentUserService.currentProfile()).thenReturn(Optional.of(driver));
        when(chatService.openChat(10L, driver))
                .thenReturn(new RideChatView(ride, List.of(), true));

        mockMvc.perform(get("/rides/{rideId}/chat", 10L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Driver</span>")));
    }

    @Test
    void confirmedRiderChatPageIdentifiesMemberAsConfirmedRider() throws Exception {
        when(chatService.openChat(10L, profile))
                .thenReturn(new RideChatView(ride, List.of(), false));

        mockMvc.perform(get("/rides/{rideId}/chat", 10L))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString(">Confirmed rider</span>")));
    }

    @Test
    void unauthorizedChatAccessRedirectsToRideDetails() throws Exception {
        when(chatService.openChat(10L, profile)).thenThrow(new RideChatAccessException(
                "Only the ride owner and confirmed riders can access this chat."));

        mockMvc.perform(get("/rides/{rideId}/chat", 10L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rides/10"))
                .andExpect(flash().attribute("errorMessage",
                        "Only the ride owner and confirmed riders can access this chat."));
    }

    @Test
    void missingRideChatRedirectsWithRideNotFoundError() throws Exception {
        when(chatService.openChat(99L, profile))
                .thenThrow(new RideChatNotFoundException("Ride not found."));

        mockMvc.perform(get("/rides/{rideId}/chat", 99L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rides/99"))
                .andExpect(flash().attribute("errorMessage", "Ride not found."));
    }

    @Test
    void pollingReturnsOnlyNewMessages() throws Exception {
        RideMessageView message = new RideMessageView(
                51L, "driver@sfu.ca", "Demo Driver", "I am outside.",
                "Jul 29, 2:15 PM", false);
        when(chatService.loadMessagesAfter(10L, profile, 50L))
                .thenReturn(List.of(message));

        mockMvc.perform(get("/rides/{rideId}/chat/messages", 10L)
                        .param("after", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(51))
                .andExpect(jsonPath("$[0].body").value("I am outside."));

        verify(chatService).loadMessagesAfter(10L, profile, 50L);
    }

    @Test
    void repeatedPollingAfterLastDisplayedMessageReturnsEmptyArray() throws Exception {
        when(chatService.loadMessagesAfter(10L, profile, 51L))
                .thenReturn(List.of());

        mockMvc.perform(get("/rides/{rideId}/chat/messages", 10L)
                        .param("after", "51"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(chatService).loadMessagesAfter(10L, profile, 51L);
    }

    @Test
    void driverSeesRiderIdentityAndMessageIsNotLabelledYou() throws Exception {
        Profile driver = new Profile(
                "driver@sfu.ca", "Demo Driver", Role.DRIVER, 0, 0);
        RideMessageView riderMessage = new RideMessageView(
                61L, "rider@sfu.ca", "Demo Rider", "I am at the pickup point.",
                "Jul 29, 2:15 PM", false);
        when(currentUserService.currentProfile()).thenReturn(Optional.of(driver));
        when(chatService.openChat(10L, driver))
                .thenReturn(new RideChatView(ride, List.of(riderMessage), true));

        mockMvc.perform(get("/rides/{rideId}/chat", 10L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-message-id=\"61\"")))
                .andExpect(content().string(containsString(">Demo Rider</p>")))
                .andExpect(content().string(containsString(">rider@sfu.ca</p>")));
    }

    @Test
    void sendMessageUsesSignedInProfileInsteadOfForgedEmail() throws Exception {
        mockMvc.perform(post("/rides/{rideId}/chat/messages", 10L)
                        .param("message", "I am at the pickup point.")
                        .param("email", "victim@sfu.ca"))
                .andExpect(status().isNoContent());

        verify(chatService).sendMessage(
                eq(10L), same(profile), eq("I am at the pickup point."));
    }

    @Test
    void validationErrorReturnsBadRequestText() throws Exception {
        when(chatService.sendMessage(10L, profile, " "))
                .thenThrow(new RideChatValidationException("Message cannot be empty."));

        mockMvc.perform(post("/rides/{rideId}/chat/messages", 10L)
                        .param("message", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Message cannot be empty."));
    }

    @Test
    void accessErrorReturnsForbiddenText() throws Exception {
        when(chatService.sendMessage(10L, profile, "Hello"))
                .thenThrow(new RideChatAccessException(
                        "Only the ride owner and confirmed riders can access this chat."));

        mockMvc.perform(post("/rides/{rideId}/chat/messages", 10L)
                        .param("message", "Hello"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(
                        "Only the ride owner and confirmed riders can access this chat."));
    }

    @Test
    void missingRideReturnsNotFoundText() throws Exception {
        when(chatService.loadMessagesAfter(99L, profile, 0L))
                .thenThrow(new RideChatNotFoundException("Ride not found."));

        mockMvc.perform(get("/rides/{rideId}/chat/messages", 99L))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Ride not found."));
    }

}
