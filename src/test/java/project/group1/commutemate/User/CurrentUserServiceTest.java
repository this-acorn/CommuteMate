package project.group1.commutemate.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import project.group1.commutemate.RewardService;
import project.group1.commutemate.RewardSummary;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Role;

/**
 * Unit tests for {@link CurrentUserService}.
 *
 * Added per review feedback (this-acorn): the switch from hardcoded 0, 0
 * points/eco-score to real RewardService-backed values had no test coverage.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RewardService rewardService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentProfile_returnsEmpty_whenNoAuthentication() {
        CurrentUserService service = new CurrentUserService(userRepository, rewardService);

        assertTrue(service.currentProfile().isEmpty());
    }

    @Test
    void currentProfile_returnsEmpty_whenAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        java.util.List.of(() -> "ROLE_ANONYMOUS")));

        CurrentUserService service = new CurrentUserService(userRepository, rewardService);

        assertTrue(service.currentProfile().isEmpty());
    }

    @Test
    void currentProfile_includesRewardTotals_fromRewardService() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("driver@sfu.ca", "n/a", java.util.List.of()));

        User user = new User();
        user.setEmail("driver@sfu.ca");
        user.setFullName("Demo Driver");
        user.setRole(Role.DRIVER);

        when(userRepository.findByEmailIgnoreCase("driver@sfu.ca")).thenReturn(Optional.of(user));
        when(rewardService.summaryForDriver("driver@sfu.ca")).thenReturn(new RewardSummary(35, 75));

        CurrentUserService service = new CurrentUserService(userRepository, rewardService);
        Optional<Profile> profile = service.currentProfile();

        assertTrue(profile.isPresent());
        assertEquals(35, profile.get().getPoints());
        assertEquals(75, profile.get().getEcoScore());
    }
}