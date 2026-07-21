package project.group1.commutemate.User;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import project.group1.commutemate.RewardService;
import project.group1.commutemate.RewardSummary;
import project.group1.commutemate.model.Profile;

/**
 * Resolves the signed-in member (if any) from the security context,
 * so public pages like the landing page can also reflect login state.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final RewardService rewardService;

    public CurrentUserService(UserRepository userRepository, RewardService rewardService) {
        this.userRepository = userRepository;
        this.rewardService = rewardService;
    }

    /** Empty when the visitor is not logged in. */
    public Optional<Profile> currentProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(auth.getName())
                .map(u -> {
                    // Epic 4 draft: was hardcoded to 0, 0 before — now backed by
                    // RewardService, keyed by email. Single query via
                    // RewardSummary instead of two separate calls (review feedback).
                    RewardSummary reward = rewardService.summaryForDriver(u.getEmail());
                    return new Profile(
                            u.getEmail(),
                            u.getFullName(),
                            u.getRole(),
                            reward.totalPoints(),
                            reward.averageEcoScore());
                });
    }
}