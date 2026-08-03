package project.group1.commutemate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.Config.SecurityConfig;
import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.User.CustomUserDetailsService;
import project.group1.commutemate.User.UserRepository;
import project.group1.commutemate.service.NotificationService;
import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;

/**
 * Unlike ProfileControllerTest, this suite leaves Spring Security's filter
 * chain enabled, so it actually exercises SecurityConfig's
 * ".anyRequest().authenticated()" rule for /profile instead of mocking
 * around it.
 *
 * <p>CustomUserDetailsService is mocked here because SecurityConfig's
 * rememberMe() DSL needs a UserDetailsService bean to wire itself up, and
 * @WebMvcTest doesn't load real @Service beans on its own.</p>
 */
@WebMvcTest(controllers = ProfileController.class)
@Import(SecurityConfig.class)
class ProfileSecurityTest {

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
    private RideService rideService;

    @MockitoBean
    private RideCoordinationService rideCoordinationService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void anonymousRequestToProfileIsRedirectedToLogIn() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth"));
    }
}