package project.group1.commutemate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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

    @Test
void recommendedDefaultRanksBySeatsEcoRatingPriceAndDeparture() {
    Ride fewerSeats = ride("Production Way-University", "SFU Burnaby - West Mall",
            3, 1, 3, 99, 5.0, 1);

    Ride lowerEco = ride("Metrotown Station", "SFU Burnaby - AQ",
            4, 1, 4, 70, 5.0, 1);

    Ride higherEco = ride("Lougheed Town Centre", "SFU Residence",
            4, 1, 5, 90, 4.8, 1);

    mockUpcoming(fewerSeats, lowerEco, higherEco);

    List<Ride> result = service.recommended("", "", "", "Recommended");

    assertEquals(List.of(higherEco, lowerEco, fewerSeats), result);
}

@Test
void recommendedUsesSelectedPriceSort() {
    Ride expensive = ride("Metrotown Station", "SFU Burnaby - AQ",
            4, 0, 8, 90, 5.0, 1);

    Ride cheap = ride("Coquitlam Central", "SFU Burnaby - AQ",
            4, 0, 3, 70, 4.8, 1);

    mockUpcoming(expensive, cheap);

    List<Ride> result = service.recommended("", "", "", "Price");

    assertEquals(List.of(cheap, expensive), result);
}

@Test
void recommendedUsesDefaultRankingWhenSortIsNull() {
    Ride lowerEco = ride("Metrotown Station", "SFU Burnaby - AQ",
            4, 1, 4, 70, 5.0, 1);

    Ride higherEco = ride("Lougheed Town Centre", "SFU Residence",
            4, 1, 5, 90, 4.8, 1);

    mockUpcoming(lowerEco, higherEco);

    List<Ride> result = service.recommended("", "", "", null);

    assertEquals(List.of(higherEco, lowerEco), result);
}

@Test
void recommendedFiltersByDepartureAndDestination() {
    Ride metrotown = ride("Metrotown Station", "SFU Burnaby - AQ",
            4, 0, 4, 82, 4.9, 1);

    Ride coquitlam = ride("Coquitlam Central", "SFU Burnaby - AQ",
            4, 0, 6, 68, 4.7, 2);

    mockUpcoming(metrotown, coquitlam);

    List<Ride> result = service.recommended("", "Metrotown", "SFU", "Recommended");

    assertEquals(List.of(metrotown), result);
}

@Test
void recommendedDoesNotIncludeFullRides() {
    Ride full = ride("Metrotown Station", "SFU Burnaby - AQ",
            1, 1, 4, 90, 5.0, 1);

    Ride available = ride("Metrotown Station", "SFU Burnaby - AQ",
            3, 0, 4, 80, 4.8, 2);

    mockUpcoming(full, available);

    List<Ride> result = service.recommended("", "Metrotown", "SFU", "Recommended");

    assertEquals(List.of(available), result);
}

private void mockUpcoming(Ride... rides) {
    when(rideRepository.findByDepartAtAfterOrderByDepartAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(rides));
}

private Ride ride(String from, String to, int seats, int seatsTaken, int price,
                  int ecoScore, double rating, int hoursFromNow) {
    return new Ride(
            "driver@sfu.ca",
            "Demo Driver",
            "DD",
            from,
            to,
            LocalDateTime.now(CLOCK).plusHours(hoursFromNow),
            seats,
            seatsTaken,
            price,
            25,
            ecoScore,
            "Test Car",
            rating,
            null
    );
}

}
