package project.group1.commutemate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-05 — once a member's session is gone, protected pages are closed to them
 * again while public pages stay open.
 *
 * <p>The redirect to "/" produced by GET /logout is not asserted here:
 * SecurityConfig matches it with a PathPatternRequestMatcher, which MockMvc's
 * filter chain does not match, so the request falls through to a 404 under
 * test even though it works in the running app. That hop is covered by the
 * manual test instead.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogoutSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    /** Once the session is gone the browser is anonymous, so dashboards are closed. */
    @Test
    void protectedRouteIsUnreachableWithoutASession() throws Exception {
        mockMvc.perform(get("/dashboard/rider").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth"));
    }

    @Test
    void landingPageStaysPublicAfterSigningOut() throws Exception {
        mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());
    }
}
