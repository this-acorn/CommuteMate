package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import project.group1.commutemate.User.CurrentUserService;

/**
 * Guards the sign-up role picker. The "both" role was dropped from the design,
 * but the button outlived the change and stayed on the deployed sign-up page,
 * so the requirements document and the running app disagreed. These tests fail
 * if it ever comes back.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthRoleChoiceViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @BeforeEach
    void signedOut() {
        when(currentUserService.currentProfile()).thenReturn(Optional.empty());
    }

    private String signUpPage() throws Exception {
        return mockMvc.perform(get("/auth").param("mode", "signup"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void signUpOffersRiderAndDriverOnly() throws Exception {
        String html = signUpPage();

        assertTrue(html.contains("data-role=\"rider\""), "rider should be offered");
        assertTrue(html.contains("data-role=\"driver\""), "driver should be offered");
        assertFalse(html.contains("data-role=\"both\""), "the 'both' role was removed from registration");
    }

    @Test
    void signUpSubmitsARoleThatIsAlsoShownAsSelected() throws Exception {
        String html = signUpPage();

        // Whatever the hidden input defaults to must be the button rendered in the
        // active style, or the form silently submits a role the member never chose.
        assertTrue(html.contains("name=\"role\" value=\"rider\""),
                "sign-up should default to the rider role");
        assertTrue(classesOf(html, "rider").contains("border-primary"),
                "the defaulted role must be the highlighted button");
        assertFalse(classesOf(html, "driver").contains("border-primary"),
                "only one role button may look selected");
    }

    /** The class attribute of the sign-up button for the given role. */
    private static String classesOf(String html, String role) {
        Matcher matcher = Pattern
                .compile("data-role=\"" + role + "\"\\s+class=\"([^\"]*)\"")
                .matcher(html);
        assertTrue(matcher.find(), "no sign-up button found for role: " + role);
        return matcher.group(1);
    }
}
