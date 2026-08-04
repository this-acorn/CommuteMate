package project.group1.commutemate.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import project.group1.commutemate.model.Role;

/**
 * Creates the demo accounts the course staff use to grade iteration 1
 * (login credentials are listed in the submission notes / README).
 *
 * <p>Runs at every startup but only inserts accounts that don't exist yet,
 * so a fresh database (e.g. after a Render redeploy) always has them while
 * registered users are never overwritten. Controlled by the
 * {@code SEED_DEMO_DATA} environment variable (defaults to enabled), so
 * these well-known, publicly-documented credentials can be turned off for
 * environments where they shouldn't exist.</p>
 */
@Component
public class DemoAccountSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedDemoData;

    public DemoAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                             @Value("${app.seed-demo-data}") boolean seedDemoData) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedDemoData = seedDemoData;
    }

    @Override
    public void run(String... args) {
        // Issue #41: this flag existed in application.properties but was
        // never actually read here, so demo accounts (with well-known
        // passwords) were always created regardless of SEED_DEMO_DATA.
        if (!seedDemoData) {
            return;
        }
        seed("driver@sfu.ca", "Demo Driver", Role.DRIVER, "demo123");
        seed("rider@sfu.ca", "Demo Rider", Role.RIDER, "demo123");
        seed("demo-rider2@sfu.ca", "Demo Member", Role.RIDER, "demo123");
    }

    private void seed(String email, String fullName, Role role, String rawPassword) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }
}
