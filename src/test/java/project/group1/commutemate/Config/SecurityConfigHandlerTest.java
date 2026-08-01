package project.group1.commutemate.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;

class SecurityConfigHandlerTest {

    private final SecurityConfig securityConfig = new SecurityConfig("test-remember-me-key");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accessDeniedRedirectsDriverToDriverDashboard() throws Exception {
        authenticateAs("ROLE_DRIVER");
        MockHttpServletResponse response = handleAccessDenied();

        assertEquals("/dashboard/driver", response.getRedirectedUrl());
    }

    @Test
    void accessDeniedRedirectsRiderToRiderDashboard() throws Exception {
        authenticateAs("ROLE_RIDER");
        MockHttpServletResponse response = handleAccessDenied();

        assertEquals("/dashboard/rider", response.getRedirectedUrl());
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

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "member@sfu.ca",
                        "unused",
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private MockHttpServletResponse handleAccessDenied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.accessDeniedHandler().handle(
                request, response, new AccessDeniedException("forbidden"));
        return response;
    }
}
