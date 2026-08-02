package project.group1.commutemate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideRequest;
import project.group1.commutemate.service.RideService;

/**
 * Unit tests for {@link RewardService}.
 *
 * RideService is mocked so these tests don't depend on its seeded demo data
 * or a running Spring context. Completion-based: a ride only counts toward
 * a driver's total once it has at least one COMPLETED request (see
 * RideCoordinationService.confirmArrival()).
 */
@ExtendWith(MockitoExtension.class)
class RewardServiceTest {

    @Mock
    private RideService rideService;

    private RewardService rewardService;

    @BeforeEach
    void setUp() {
        rewardService = new RewardService(rideService);
    }

    private Ride ride(String driverEmail, int points, int ecoScore) {
        return new Ride(
                driverEmail, "Test Driver", "TD",
                "Somewhere", "SFU Burnaby",
                LocalDateTime.now().plusDays(1), 3, 0, 4,
                points, ecoScore,
                "Test Car", 5.0, null
        );
    }

    /** Adds a rider request in the given status to a ride, mimicking a real booking. */
    private void addRequest(Ride ride, String riderEmail, RequestStatus status) {
        RideRequest request = new RideRequest(ride, riderEmail, "Test Rider");
        request.setStatus(status);
        ride.getRequests().add(request);
    }

    @Test
    void summaryForDriver_countsOnlyRidesWithACompletedRider() {
        Ride completed = ride("alex@sfu.ca", 20, 80);
        addRequest(completed, "rider1@sfu.ca", RequestStatus.COMPLETED);

        Ride stillOnlyPublished = ride("alex@sfu.ca", 15, 70);
        // no requests at all — never boarded, never arrived

        when(rideService.findByDriverEmail("alex@sfu.ca"))
                .thenReturn(List.of(completed, stillOnlyPublished));

        RewardSummary summary = rewardService.summaryForDriver("alex@sfu.ca");

        assertEquals(20, summary.totalPoints());
        assertEquals(80, summary.averageEcoScore());
    }

    @Test
    void summaryForDriver_ignoresRideWithOnlyPendingOrConfirmedRequests() {
        Ride notYetCompleted = ride("alex@sfu.ca", 20, 80);
        addRequest(notYetCompleted, "rider1@sfu.ca", RequestStatus.CONFIRMED);
        addRequest(notYetCompleted, "rider2@sfu.ca", RequestStatus.BOARDING_CONFIRMED);

        when(rideService.findByDriverEmail("alex@sfu.ca"))
                .thenReturn(List.of(notYetCompleted));

        RewardSummary summary = rewardService.summaryForDriver("alex@sfu.ca");

        assertEquals(0, summary.totalPoints());
        assertEquals(0, summary.averageEcoScore());
    }

    @Test
    void summaryForDriver_sumsMultipleCompletedRidesAndAveragesEcoScore() {
        Ride ride1 = ride("alex@sfu.ca", 20, 80);
        addRequest(ride1, "rider1@sfu.ca", RequestStatus.COMPLETED);

        Ride ride2 = ride("alex@sfu.ca", 15, 70);
        addRequest(ride2, "rider2@sfu.ca", RequestStatus.COMPLETED);

        when(rideService.findByDriverEmail("alex@sfu.ca"))
                .thenReturn(List.of(ride1, ride2));

        RewardSummary summary = rewardService.summaryForDriver("alex@sfu.ca");

        assertEquals(35, summary.totalPoints());
        assertEquals(75, summary.averageEcoScore());
    }

    @Test
    void summaryForDriver_returnsEmpty_whenDriverHasNoRides() {
        when(rideService.findByDriverEmail("nobody@sfu.ca")).thenReturn(List.of());

        RewardSummary summary = rewardService.summaryForDriver("nobody@sfu.ca");

        assertEquals(0, summary.totalPoints());
        assertEquals(0, summary.averageEcoScore());
    }

    @Test
    void summaryForDriver_ecoScoreRoundsDown_onUnevenDivision() {
        // (80 + 81) / 2 = 80.5 -> integer division rounds down to 80
        Ride ride1 = ride("marcus@sfu.ca", 10, 80);
        addRequest(ride1, "rider1@sfu.ca", RequestStatus.COMPLETED);

        Ride ride2 = ride("marcus@sfu.ca", 10, 81);
        addRequest(ride2, "rider2@sfu.ca", RequestStatus.COMPLETED);

        when(rideService.findByDriverEmail("marcus@sfu.ca"))
                .thenReturn(List.of(ride1, ride2));

        RewardSummary summary = rewardService.summaryForDriver("marcus@sfu.ca");

        assertEquals(20, summary.totalPoints());
        assertEquals(80, summary.averageEcoScore());
    }

    @Test
    void summaryForDriver_onlyQueriesRideServiceOnce() {
        Ride ride1 = ride("priya@sfu.ca", 10, 90);
        addRequest(ride1, "rider1@sfu.ca", RequestStatus.COMPLETED);

        when(rideService.findByDriverEmail("priya@sfu.ca")).thenReturn(List.of(ride1));

        rewardService.summaryForDriver("priya@sfu.ca");

        org.mockito.Mockito.verify(rideService, org.mockito.Mockito.times(1))
                .findByDriverEmail("priya@sfu.ca");
    }
}
