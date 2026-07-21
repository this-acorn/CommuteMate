package project.group1.commutemate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.repository.RideRepository;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-16T19:00:00Z"), VANCOUVER);

    @Mock
    private RideRepository rideRepository;

    private RideService service;

    @BeforeEach
    void setUp() {
        service = new RideService(rideRepository, CLOCK);
    }

    @Test
    void createStoresAuthenticatedEmailAsOwner() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ride created = service.create("DRIVER@SFU.CA", "Demo Driver",
                "Metrotown Station", "SFU Residence",
                LocalDateTime.now(CLOCK).plusDays(1), 3, 4, "Meet outside");

        assertEquals("driver@sfu.ca", created.getDriverEmail());
        assertEquals("Demo Driver", created.getDriver());
        assertEquals(0, created.getSeatsTaken());
    }

    @Test
    void createRejectsPastDeparture() {
        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.create("driver@sfu.ca", "Demo Driver",
                        "Metrotown Station", "SFU Residence",
                        LocalDateTime.now(CLOCK).minusMinutes(1), 3, 4, null));

        assertEquals("Departure must be in the future.", error.getMessage());
    }

    @Test
    void createRejectsStopsOutsideTheSelectableList() {
        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.create("driver@sfu.ca", "Demo Driver", "My House", "SFU Residence",
                        LocalDateTime.now(CLOCK).plusDays(1), 3, 4, null));

        assertEquals("Choose a pickup and destination from the list.", error.getMessage());
    }

    @Test
    void createRejectsIdenticalPickupAndDestination() {
        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.create("driver@sfu.ca", "Demo Driver",
                        "SFU Residence", "SFU Residence",
                        LocalDateTime.now(CLOCK).plusDays(1), 3, 4, null));

        assertEquals("Pickup and destination must be different.", error.getMessage());
    }
}
