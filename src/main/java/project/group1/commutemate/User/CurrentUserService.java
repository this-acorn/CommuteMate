package project.group1.commutemate.User;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import project.group1.commutemate.RewardService;
import project.group1.commutemate.RewardSummary;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.Role;

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
                    // Drivers earn per-ride points/eco-score; riders earn a
                    // flat amount per completed ride and have no eco-score.
                    int points;
                    int ecoScore;
                    if (u.getRole() == Role.DRIVER) {
                        RewardSummary reward = rewardService.summaryForDriver(u.getEmail());
                        points = reward.totalPoints();
                        ecoScore = reward.averageEcoScore();
                    } else {
                        points = rewardService.totalPointsForRider(u.getEmail());
                        ecoScore = 0;
                    }
                    return new Profile(
                            u.getEmail(),
                            u.getFullName(),
                            u.getRole(),
                            points,
                            ecoScore);
                });
    }
}