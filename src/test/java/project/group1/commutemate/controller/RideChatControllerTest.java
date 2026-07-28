package project.group1.commutemate.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideChatView;
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
    void unauthorizedChatAccessRedirectsToRideDetails() throws Exception {
        when(chatService.openChat(10L, profile)).thenThrow(new RideOperationException(
                "Only the ride owner and confirmed riders can access this chat."));

        mockMvc.perform(get("/rides/{rideId}/chat", 10L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rides/10"))
                .andExpect(flash().attribute("errorMessage",
                        "Only the ride owner and confirmed riders can access this chat."));
    }

    @Test
    void sendMessageUsesSignedInProfileInsteadOfForgedEmail() throws Exception {
        mockMvc.perform(post("/rides/{rideId}/chat/messages", 10L)
                        .param("message", "I am at the pickup point.")
                        .param("email", "victim@sfu.ca"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rides/10/chat"))
                .andExpect(flash().attribute("successMessage", "Message sent."));

        verify(chatService).sendMessage(
                eq(10L), same(profile), eq("I am at the pickup point."));
    }

    @Test
    void validationErrorReturnsToChatWithMessage() throws Exception {
        when(chatService.sendMessage(10L, profile, " "))
                .thenThrow(new RideOperationException("Message cannot be empty."));

        mockMvc.perform(post("/rides/{rideId}/chat/messages", 10L)
                        .param("message", " "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rides/10/chat"))
                .andExpect(flash().attribute("errorMessage", "Message cannot be empty."));
    }
}
