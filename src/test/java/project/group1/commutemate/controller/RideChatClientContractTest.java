package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Java-side contract checks for the small browser client used by the ride chat.
 *
 * <p>The project intentionally keeps all automated test classes in Java. These
 * checks protect the client behaviors that cannot be observed through MockMvc,
 * while the controller and service tests verify the corresponding HTTP and
 * persistence behavior.</p>
 */
class RideChatClientContractTest {

    private String clientSource;

    @BeforeEach
    void loadClientSource() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/js/ride-chat.js");
        try (InputStream input = resource.getInputStream()) {
            clientSource = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void expiredSessionDisplaysErrorAndStopsFurtherPolling() {
        assertAppearsInOrder(
                "if (wasRedirectedToLogin(response) || response.status === 401) {",
                "showError(\"Your session expired. Sign in again before sending another message.\");",
                "stopPolling();",
                "return true;");
        assertTrue(clientSource.contains("window.clearInterval(pollingTimer);"));
        assertTrue(clientSource.contains("pollingStopped = true;"));
    }

    @Test
    void accessRemovalDisplaysErrorAndStopsFurtherPolling() {
        assertAppearsInOrder(
                "if (response.status === 403) {",
                "showError(\"You no longer have access to this chat.\");",
                "stopPolling();",
                "return true;");
    }

    @Test
    void alreadyDisplayedMessageIdsAreNotAppendedAgain() {
        assertTrue(clientSource.contains(
                "Number(item.id) <= lastMessageId"));
        assertAppearsInOrder(
                "messageList.appendChild(article);",
                "lastMessageId = Math.max(lastMessageId, Number(item.id));");
    }

    @Test
    void successfulSendClearsComposerAndLoadsMessageWithoutPageReload() {
        assertAppearsInOrder(
                "if (response.status !== 204) {",
                "message.value = \"\";",
                "resizeMessageBox();",
                "await loadNewMessages(true);");
        assertFalse(clientSource.contains("location.reload("));
        assertFalse(clientSource.contains("window.location.reload("));
    }

    @Test
    void enterSubmitsAndShiftEnterRemainsAvailableForNewLine() {
        assertAppearsInOrder(
                "message.addEventListener(\"keydown\"",
                "event.key === \"Enter\" && !event.shiftKey",
                "event.preventDefault();",
                "form.requestSubmit();");
    }

    private void assertAppearsInOrder(String... fragments) {
        int previous = -1;
        for (String fragment : fragments) {
            int position = clientSource.indexOf(fragment, previous + 1);
            assertTrue(position > previous,
                    () -> "Expected ride-chat.js to contain in order: " + fragment);
            previous = position;
        }
    }
}
