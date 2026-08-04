package project.group1.commutemate.User;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DemoAccountSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // Issue #41: app.seed-demo-data (SEED_DEMO_DATA) existed as a config
    // property but was never actually read by the seeder.
    @Test
    void doesNotSeedAnyAccounts_whenSeedDemoDataIsFalse() {
        DemoAccountSeeder seeder = new DemoAccountSeeder(userRepository, passwordEncoder, false);

        seeder.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void seedsAllThreeDemoAccounts_whenSeedDemoDataIsTrue() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        DemoAccountSeeder seeder = new DemoAccountSeeder(userRepository, passwordEncoder, true);

        seeder.run();

        verify(userRepository, times(3)).save(any(User.class));
    }

    @Test
    void doesNotOverwriteAnAccountThatAlreadyExists() {
        when(userRepository.findByEmailIgnoreCase("driver@sfu.ca"))
                .thenReturn(Optional.of(new User()));
        when(userRepository.findByEmailIgnoreCase(eq("rider@sfu.ca")))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(eq("demo-rider2@sfu.ca")))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        DemoAccountSeeder seeder = new DemoAccountSeeder(userRepository, passwordEncoder, true);

        seeder.run();

        // Only the two accounts that didn't already exist get saved
        verify(userRepository, times(2)).save(any(User.class));
    }
}
