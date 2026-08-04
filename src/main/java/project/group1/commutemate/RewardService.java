package project.group1.commutemate;

import java.util.List;

import org.springframework.stereotype.Service;

import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideRequest;
import project.group1.commutemate.service.RideCoordinationService;
import project.group1.commutemate.service.RideService;

/**
 * Epic 4 — Incentives & Rewards.
 *
 * ============================== COMPLETION-BASED (resolved) ==============================
 * Previously this was a "draft" awarding points for any published ride,
 * even if it never happened. Now that Epic 5 has a real post-ride workflow
 * (RideCoordinationService.confirmBoarding()/confirmArrival()), a ride only
 * counts toward a driver's total once at least one of its ride requests has
 * reached RequestStatus.COMPLETED — i.e. someone actually boarded and the
 * driver confirmed arrival. Rides that were only ever published, or never
 * completed, don't count. This also means a ride keeps counting after it
 * departs (no longer "upcoming"), since RideService.findByDriverEmail is an
 * all-time lookup, not limited to future rides.
 * ===========================================================================================
 *
 * Driver-keyed by email (not full name) to avoid two different drivers with
 * the same display name sharing a point total.
 */
@Service
public class RewardService {

    // Flat rate per completed ride — riders don't have a "seats offered"
    // variable to scale by like drivers do. All-time cumulative, matching
    // driver points (see summaryForDriver).
    private static final int RIDER_POINTS_PER_COMPLETED_RIDE = 10;

    private final RideService rideService;
    private final RideCoordinationService coordinationService;

    public RewardService(RideService rideService, RideCoordinationService coordinationService) {
        this.rideService = rideService;
        this.coordinationService = coordinationService;
    }

    /**
     * Points + eco-score for a driver, computed in a single pass over their
     * rides (one query) instead of two separate calls. Only rides with at
     * least one COMPLETED request count toward the total.
     */
    public RewardSummary summaryForDriver(String driverEmail) {
        List<Ride> rides = rideService.findByDriverEmail(driverEmail);
        if (rides.isEmpty()) {
            return RewardSummary.EMPTY;
        }

        int totalPoints = 0;
        int ecoScoreSum = 0;
        int completedRideCount = 0;
        for (Ride ride : rides) {
            if (!hasCompletedRider(ride)) {
                continue;
            }
            totalPoints += ride.getPoints();
            ecoScoreSum += ride.getEcoScore();
            completedRideCount++;
        }

        if (completedRideCount == 0) {
            return RewardSummary.EMPTY;
        }
        return new RewardSummary(totalPoints, ecoScoreSum / completedRideCount);
    }

    /**
     * All-time reward points for a rider — flat amount per COMPLETED
     * request. No spending mechanism yet (future item).
     */
    public int totalPointsForRider(String riderEmail) {
        List<RideRequest> requests = coordinationService.findRequestsForRider(riderEmail);
        long completedCount = requests.stream()
                .filter(request -> request.getStatus() == RequestStatus.COMPLETED)
                .count();
        return (int) (completedCount * RIDER_POINTS_PER_COMPLETED_RIDE);
    }

    private boolean hasCompletedRider(Ride ride) {
        return ride.getRequests().stream()
                .anyMatch(request -> request.getStatus() == RequestStatus.COMPLETED);
    }
}
