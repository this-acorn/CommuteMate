package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AuthEnterKeyScriptTest {

    @Test
    void loginCredentialFieldsSubmitOnEnter() throws Exception {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("static/js/commutemate.js")) {
            assertNotNull(stream);
            String script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(script.contains("event.key !== \"Enter\""));
            assertTrue(script.contains("form.requestSubmit(submitBtn)"));
            assertTrue(script.contains("modeInput.value !== \"login\""));
        }
    }
}
