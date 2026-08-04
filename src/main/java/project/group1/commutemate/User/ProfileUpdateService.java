package project.group1.commutemate.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;

/**
 * Updates a member's full name and keeps every denormalized copy of it in
 * sync — Ride.driver/driverInitials for a driver's rides, and
 * RideRequest.riderName for a rider's requests.
 *
 * <p>All three writes happen inside a single transaction. If any of them
 * fails, none of them commit, so the profile name can never end up out of
 * sync with what's shown on a ride card or ride request.</p>
 */
@Service
public class ProfileUpdateService {

    private final UserRepository userRepository;
    private final RideService rideService;
    private final RideCoordinationService rideCoordinationService;

    public ProfileUpdateService(UserRepository userRepository,
                                 RideService rideService,
                                 RideCoordinationService rideCoordinationService) {
        this.userRepository = userRepository;
        this.rideService = rideService;
        this.rideCoordinationService = rideCoordinationService;
    }

    @Transactional
    public void renameUser(String email, String newFullName) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in member has no account row"));
        user.setFullName(newFullName);
        userRepository.save(user);

        rideService.renameDriver(email, newFullName);
        rideCoordinationService.renameRider(email, newFullName);
    }
}