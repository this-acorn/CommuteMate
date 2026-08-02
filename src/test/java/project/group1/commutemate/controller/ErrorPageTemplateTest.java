package project.group1.commutemate.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ErrorPageTemplateTest {

    @Test
    void customErrorPagesAreAvailable() {
        ClassLoader loader = getClass().getClassLoader();
        assertNotNull(loader.getResource("templates/error/403.html"));
        assertNotNull(loader.getResource("templates/error/404.html"));
        assertNotNull(loader.getResource("templates/error/500.html"));
        assertNotNull(loader.getResource("templates/error.html"));
    }
}
