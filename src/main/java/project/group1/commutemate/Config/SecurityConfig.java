package project.group1.commutemate.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Signs the remember-me cookie. It has to stay the same across restarts —
     * Spring Security otherwise invents a random key at startup, which would
     * invalidate every outstanding cookie the moment Render replaces the
     * container. Set REMEMBER_ME_KEY in the deployment environment.
     */
    private final String rememberMeKey;

    public SecurityConfig(@Value("${app.remember-me.key}") String rememberMeKey) {
        this.rememberMeKey = rememberMeKey;
    }

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
        return (request, response, exception) -> {
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
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/auth", "/register", "/login",
                        "/css/**", "/js/**", "/images/**", "/error").permitAll()
                // Driver features
                .requestMatchers("/dashboard/driver", "/rides/create")
                        .hasRole("DRIVER")
                .requestMatchers(HttpMethod.POST, "/ride-requests/*/confirm", "/ride-requests/*/reject")
                        .hasRole("DRIVER")
                .requestMatchers(HttpMethod.POST, "/rides/*/delete")
                        .hasRole("DRIVER")
                // Rider features
                .requestMatchers("/dashboard/rider", "/rides/available")
                        .hasRole("RIDER")
                .requestMatchers(HttpMethod.POST, "/rides/*/requests")
                        .hasRole("RIDER")
                .requestMatchers(HttpMethod.POST, "/ride-requests/*/cancel")
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
            // Outlives the session store as well: a member who ticked "Keep me
            // signed in" is re-authenticated from the cookie even after their
            // stored session has expired.
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .rememberMeParameter("remember-me")
                .tokenValiditySeconds(30 * 24 * 60 * 60)
            )
            .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()))
            .logout(logout -> logout
                .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/logout"))
                .logoutSuccessUrl("/")
                .permitAll()
            );

        return http.build();
    }
}
