package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Guards the driver dashboard shortcut for every status that still permits chat. */
class DriverDashboardChatLinkContractTest {

    @Test
    void chatShortcutRemainsVisibleAfterBoardingAndCompletion() throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/dashboard-driver.html");
        String template;
        try (InputStream input = resource.getInputStream()) {
            template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        int linkText = template.indexOf("Open ride chat");
        assertTrue(linkText >= 0, "Driver dashboard must contain the ride-chat shortcut.");

        int conditionStart = template.lastIndexOf("th:if=", linkText);
        assertTrue(conditionStart >= 0, "Ride-chat shortcut must be guarded by a status condition.");

        String condition = template.substring(conditionStart, linkText);
        assertTrue(condition.contains("'CONFIRMED'"));
        assertTrue(condition.contains("'BOARDING_CONFIRMED'"));
        assertTrue(condition.contains("'COMPLETED'"));
    }
}
