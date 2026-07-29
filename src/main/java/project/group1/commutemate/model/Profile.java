package project.group1.commutemate.model;

/**
 * A verified SFU student's CommuteMate profile.
 * Mirrors the {@code profiles} table from the original design
 * (email, full_name, role, points, eco_score).
 */
public class Profile {

    private String email;
    private String fullName;
    private Role role = Role.RIDER;
    private int points;
    private int ecoScore;

    public Profile() {
    }

    public Profile(String email, String fullName, Role role, int points, int ecoScore) {
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.points = points;
        this.ecoScore = ecoScore;
    }

            /** True when the profile role is DRIVER. */
        public boolean isDriverCapable() {
         return role == Role.DRIVER;
        }

        /** True when the profile role is RIDER. */
        public boolean isRiderCapable() {
         return role == Role.RIDER;
        }

    /** Initials shown in avatar bubbles, e.g. "Alex Chen" -> "AC". */
    public String getInitials() {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return sb.toString();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getEcoScore() {
        return ecoScore;
    }

    public void setEcoScore(int ecoScore) {
        this.ecoScore = ecoScore;
    }
}
