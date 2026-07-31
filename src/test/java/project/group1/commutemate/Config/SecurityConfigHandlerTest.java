package project.group1.commutemate.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

class SecurityConfigHandlerTest {

    private final SecurityConfig securityConfig = new SecurityConfig("test-remember-me-key");

    @Test
    void accessDeniedUsesCustom403ErrorFlow() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.accessDeniedHandler().handle(
                request, response, new AccessDeniedException("forbidden"));

        assertEquals(403, response.getStatus());
    }

    @Test
    void failedLoginKeepsEmailButNeverPassword() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("email", " driver@sfu.ca ");
        request.setParameter("password", "secret-password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.failureHandler().onAuthenticationFailure(
                request, response, new AuthenticationException("bad credentials") { });

        assertEquals("driver@sfu.ca", request.getSession().getAttribute("LAST_LOGIN_EMAIL"));
        assertEquals(null, request.getSession().getAttribute("password"));
        assertEquals("/auth?mode=login&error", response.getRedirectedUrl());
    }
}
