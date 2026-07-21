package project.group1.commutemate;

import java.util.List;

import org.springframework.stereotype.Service;

import project.group1.commutemate.model.Ride;
import project.group1.commutemate.service.RideService;

/**
 * Epic 4 — Incentives & Rewards.
 *
 * ============================== DRAFT — STAYING DRAFT UNTIL EPIC 5 LANDS ==============================
 * Per review discussion (this-acorn, sqwhh, jaskarndeogun-cmyk): points/eco-score
 * should be awarded for COMPLETED rides, not simply for rides a driver has
 * published — right now a driver gets points immediately on publish, even if
 * the ride never happens, and points vanish silently if the ride is deleted.
 * Epic 5 doesn't have a "completed ride" concept yet, so this can't be fixed
 * correctly until that lands. Keeping this in draft per team consensus.
 *
 * Also flagged: eco-score is currently computed by Ride/RideService from
 * seats OFFERED, not seats actually FILLED — that's an Epic 5 concern, not
 * fixable from this file.
 * ========================================================================================================
 *
 * Driver-keyed by email (not full name) to avoid two different drivers with
 * the same display name sharing a point total — matches RideService's
 * findUpcomingByDriverEmail after the Epic 5 PR #10 merge.
 */
@Service
public class RewardService {

    private final RideService rideService;

    public RewardService(RideService rideService) {
        this.rideService = rideService;
    }

    /**
     * Points + eco-score for a driver, computed in a single pass over their
     * rides (one query) instead of two separate calls — per review feedback
     * flagging the cost of querying rides twice per profile load.
     */
    public RewardSummary summaryForDriver(String driverEmail) {
        List<Ride> rides = rideService.findUpcomingByDriverEmail(driverEmail);
        if (rides.isEmpty()) {
            return RewardSummary.EMPTY;
        }

        int totalPoints = 0;
        int ecoScoreSum = 0;
        for (Ride ride : rides) {
            totalPoints += ride.getPoints();
            ecoScoreSum += ride.getEcoScore();
        }

        return new RewardSummary(totalPoints, ecoScoreSum / rides.size());
    }
}