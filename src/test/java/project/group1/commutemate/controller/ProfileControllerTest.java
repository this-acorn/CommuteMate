package project.group1.commutemate.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.User.ProfileUpdateService;
import project.group1.commutemate.User.User;
import project.group1.commutemate.User.UserRepository;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.service.NotificationService;

@WebMvcTest(controllers = ProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfileControllerTest {

    private static final String SIGNED_IN_EMAIL = "rider@sfu.ca";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private ProfileUpdateService profileUpdateService;

    private User storedUser;

    @BeforeEach
    void signedInAsRider() {
        storedUser = new User();
        storedUser.setEmail(SIGNED_IN_EMAIL);
        storedUser.setFullName("Riley Rider");
        storedUser.setPassword("encoded-old-password");
        storedUser.setRole(Role.RIDER);

        when(currentUserService.currentProfile())
                .thenReturn(Optional.of(new Profile(SIGNED_IN_EMAIL, "Riley Rider", Role.RIDER, 42, 88)));
        when(notificationService.unreadCountFor(SIGNED_IN_EMAIL)).thenReturn(0L);
        when(userRepository.findByEmailIgnoreCase(SIGNED_IN_EMAIL)).thenReturn(Optional.of(storedUser));
    }

    @Test
    void profilePageShowsAccountDetails() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Riley Rider")))
                .andExpect(content().string(containsString(SIGNED_IN_EMAIL)))
                .andExpect(content().string(containsString("rider")))
                .andExpect(content().string(containsString("42")))
                .andExpect(content().string(containsString("88")));
    }

    @Test
    void updatingNameRedirectsAndShowsSuccessMessage() throws Exception {
        mockMvc.perform(post("/profile/name").param("fullName", "  Riley R. Rider  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("nameSuccess", "Name updated."));

        verify(profileUpdateService).renameUser(SIGNED_IN_EMAIL, "Riley R. Rider");
    }

    @Test
    void blankNameIsRejectedAndNothingIsSaved() throws Exception {
        mockMvc.perform(post("/profile/name").param("fullName", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("blank")));

        verify(profileUpdateService, never()).renameUser(any(), any());
    }

    @Test
    void wrongCurrentPasswordIsRejectedAndNothingIsSaved() throws Exception {
        when(passwordEncoder.matches("wrong-password", "encoded-old-password")).thenReturn(false);

        mockMvc.perform(post("/profile/password")
                        .param("currentPassword", "wrong-password")
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("incorrect")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void mismatchedNewPasswordsAreRejectedAndNothingIsSaved() throws Exception {
        when(passwordEncoder.matches("old-password", "encoded-old-password")).thenReturn(true);

        mockMvc.perform(post("/profile/password")
                        .param("currentPassword", "old-password")
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "different123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("do not match")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void correctCurrentPasswordUpdatesAndShowsSuccessMessage() throws Exception {
        when(passwordEncoder.matches("old-password", "encoded-old-password")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("encoded-new-password");

        mockMvc.perform(post("/profile/password")
                        .param("currentPassword", "old-password")
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("passwordSuccess", "Password updated."));

        verify(userRepository).save(argThat(u -> "encoded-new-password".equals(u.getPassword())));
    }
}