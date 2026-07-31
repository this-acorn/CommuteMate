package project.group1.commutemate.User;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RegisterController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegisterFormValidationViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void invalidRegistrationKeepsSafeValuesAndMarksInvalidFields() throws Exception {
        when(userRepository.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.empty());

        String html = mockMvc.perform(post("/register")
                        .param("fullName", "Alex Chen")
                        .param("email", "alex@example.com")
                        .param("password", "123")
                        .param("role", "driver"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth"))
                .andExpect(model().attribute("fullName", "Alex Chen"))
                .andExpect(model().attribute("email", "alex@example.com"))
                .andExpect(model().attribute("selectedRole", "driver"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(html.contains("value=\"Alex Chen\""));
        assertTrue(html.contains("value=\"alex@example.com\""));
        assertTrue(html.contains("name=\"role\" value=\"driver\""));
        assertTrue(html.contains("field-error"));

        // Passwords are deliberately not put back into the HTML after an error.
        assertFalse(html.contains("value=\"123\""));
    }
}
