package project.group1.commutemate;

/**
 * A driver's aggregated reward totals, computed from a single pass over
 * their rides. Introduced per review feedback to avoid RewardService
 * querying rides twice (once for points, once for eco-score) on every
 * profile load.
 */
public record RewardSummary(int totalPoints, int averageEcoScore) {

    public static final RewardSummary EMPTY = new RewardSummary(0, 0);
}