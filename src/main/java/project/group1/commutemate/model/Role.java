package project.group1.commutemate.model;

/**
 * How a member wants to use CommuteMate.
 *
 * <p>The original design also had a {@code both} role, but a member who is
 * simultaneously offering and requesting rides made the dashboards and the
 * ride-request rules ambiguous, so registration offers driver or rider only.</p>
 */
public enum Role {
    DRIVER,
    RIDER;

    /**
     * Parse a role from user input. The registration form is validated before
     * this runs, so anything unexpected here is a malformed request rather than
     * a choice we can honour — fall back to the least-privileged role.
     */
    public static Role from(String value) {
        if (value == null) {
            return RIDER;
        }

        return switch (value.trim().toUpperCase()) {
            case "DRIVER" -> DRIVER;
            default -> RIDER;
        };
    }

    /** Lower-case label used in the UI, e.g. "driver". */
    public String label() {
        return name().toLowerCase();
    }
}
