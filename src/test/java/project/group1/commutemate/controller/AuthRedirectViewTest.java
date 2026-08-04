package project.group1.commutemate.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Role;

/**
 * US-03 — a signed-in member who navigates back to /auth is sent to their own
 * dashboard instead of being shown a login form, while anonymous visitors
 * still get the form.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthRedirectViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    void signedInDriverIsSentToTheDriverDashboard() throws Exception {
        signedInAs(Role.DRIVER);

        mockMvc.perform(get("/auth"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/driver"));
    }

    @Test
    void signedInRiderIsSentToTheRiderDashboard() throws Exception {
        signedInAs(Role.RIDER);

        mockMvc.perform(get("/auth"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/rider"));
    }

    /** The redirect must not swallow the sign-up form for logged-out visitors. */
    @Test
    void anonymousVisitorStillSeesTheSignUpForm() throws Exception {
        when(currentUserService.currentProfile()).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth").param("mode", "signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth"))
                .andExpect(model().attribute("authenticated", false))
                .andExpect(model().attribute("mode", "signup"));
    }

    @Test
    void postRegistrationVisitorSeesTheAccountCreatedMessage() throws Exception {
        when(currentUserService.currentProfile()).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth").param("mode", "login").param("registered", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("auth"))
                .andExpect(model().attribute("mode", "login"))
                .andExpect(content().string(containsString("Account created")));
    }

    private void signedInAs(Role role) {
        when(currentUserService.currentProfile()).thenReturn(
                Optional.of(new Profile("member@sfu.ca", "Signed In Member", role, 0, 0)));
    }
}
