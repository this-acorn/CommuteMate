package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Guards the unread-message indicator on ride-chat shortcuts. */
class RideChatUnreadIndicatorViewTest {

    @Test
    void driverDashboardShowsUnreadDotForMatchingRide() throws IOException {
        String template = readTemplate("dashboard-driver.html");

        assertTrue(template.contains("unreadChatRideIds.contains(request.ride.id)"));
        assertTrue(template.contains("unreadChatRideIds.contains(r.id)"));
        assertTrue(template.contains("chat-unread-dot"));
        assertTrue(template.contains("data-chat-dashboard"));
        assertTrue(template.contains("data-chat-link"));
        assertTrue(template.contains("data-chat-unread-dot"));
        assertTrue(template.contains("th:data-ride-id"));
        assertTrue(template.contains("Unread messages"));
    }

    @Test
    void riderDashboardShowsUnreadDotForMatchingRide() throws IOException {
        String template = readTemplate("dashboard-rider.html");

        assertTrue(template.contains("unreadChatRideIds.contains(request.ride.id)"));
        assertTrue(template.contains("unreadChatRideIds.contains(r.id)"));
        assertTrue(template.contains("chat-unread-dot"));
        assertTrue(template.contains("data-chat-dashboard"));
        assertTrue(template.contains("data-chat-link"));
        assertTrue(template.contains("data-chat-unread-dot"));
        assertTrue(template.contains("th:data-ride-id"));
        assertTrue(template.contains("Unread messages"));
    }

    private static String readTemplate(String name) throws IOException {
        return readResource("templates/" + name);
    }

    @Test
    void dashboardScriptClearsBadgeAndRefreshesCachedDashboard() throws IOException {
        String script = readResource("static/js/commutemate.js");

        assertTrue(script.contains("initChatUnreadIndicators()"));
        assertTrue(script.contains("[data-chat-link]"));
        assertTrue(script.contains("[data-chat-unread-dot]"));
        assertTrue(script.contains("dot.remove()"));
        assertTrue(script.contains("event.persisted"));
        assertTrue(script.contains("window.location.reload()"));
    }

    private static String readResource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
