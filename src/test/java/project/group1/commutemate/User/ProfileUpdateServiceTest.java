package project.group1.commutemate.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;

@ExtendWith(MockitoExtension.class)
class ProfileUpdateServiceTest {

    private static final String EMAIL = "rider@sfu.ca";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RideService rideService;

    @Mock
    private RideCoordinationService rideCoordinationService;

    private ProfileUpdateService profileUpdateService;
    private User storedUser;

    @BeforeEach
    void setUp() {
        profileUpdateService = new ProfileUpdateService(userRepository, rideService, rideCoordinationService);

        storedUser = new User();
        storedUser.setEmail(EMAIL);
        storedUser.setFullName("Old Name");
    }

    @Test
    void renameUserUpdatesTheAccountAndBothDenormalizedCopies() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(storedUser));

        profileUpdateService.renameUser(EMAIL, "New Name");

        assertEquals("New Name", storedUser.getFullName());
        verify(userRepository).save(storedUser);
        verify(rideService).renameDriver(EMAIL, "New Name");
        verify(rideCoordinationService).renameRider(EMAIL, "New Name");
    }

    @Test
    void unknownEmailUpdatesNothing() {
        when(userRepository.findByEmailIgnoreCase("ghost@sfu.ca")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> profileUpdateService.renameUser("ghost@sfu.ca", "New Name"));

        verify(rideService, never()).renameDriver(any(), any());
        verify(rideCoordinationService, never()).renameRider(any(), any());
    }
}