package project.group1.commutemate.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** After login, land on the dashboard that matches the member's role. */
    @Bean
    public AuthenticationSuccessHandler successHandler() {
        return (request, response, authentication) ->
                response.sendRedirect(hasRole(authentication, "ROLE_DRIVER")
                        ? "/dashboard/driver"
                        : "/dashboard/rider");
    }

    /**
     * A driver-only member hitting a rider page (or vice versa) is bounced to
     * their own dashboard instead of seeing a bare 403 page.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        RequestMatcher chatMessageEndpoint = PathPatternRequestMatcher
                .withDefaults()
                .matcher("/rides/{rideId}/chat/messages");

        return (request, response, exception) -> {
            // Fetch-based chat calls must receive a real HTTP status. Redirecting
            // to a dashboard would make JavaScript treat a rejected request as a
            // successful HTML response.
            if (chatMessageEndpoint.matches(request)) {
                response.sendError(HttpStatus.FORBIDDEN.value());
                return;
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            response.sendRedirect(auth != null && hasRole(auth, "ROLE_DRIVER")
                    ? "/dashboard/driver"
                    : "/dashboard/rider");
        };
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        RequestMatcher chatMessageEndpoint = PathPatternRequestMatcher
                .withDefaults()
                .matcher("/rides/{rideId}/chat/messages");

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/auth", "/register", "/login",
                        "/css/**", "/js/**", "/images/**", "/error").permitAll()
                // Driver features
                .requestMatchers("/dashboard/driver", "/rides/create")
                        .hasRole("DRIVER")
                .requestMatchers(HttpMethod.POST, "/rides/*/requests")
                        .hasRole("RIDER")
                .requestMatchers(HttpMethod.POST, "/ride-requests/*/confirm", "/ride-requests/*/reject")
                        .hasRole("DRIVER")
                .requestMatchers(HttpMethod.POST, "/ride-requests/*/cancel")
                        .hasRole("RIDER")
                .requestMatchers(HttpMethod.POST, "/rides/*/delete")
                        .hasRole("DRIVER")
                .requestMatchers("/rides/*/chat", "/rides/*/chat/messages")
                        .authenticated()
                // Rider features
                .requestMatchers("/dashboard/rider", "/rides/available")
                        .hasRole("RIDER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/auth")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(successHandler())
                .failureUrl("/auth?mode=login&error")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                // Fetch calls must receive an HTTP status.
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        chatMessageEndpoint)
                .accessDeniedHandler(accessDeniedHandler())
            )
            .logout(logout -> logout
                .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/logout"))
                .logoutSuccessUrl("/")
                .permitAll()
            );

        return http.build();
    }
}
