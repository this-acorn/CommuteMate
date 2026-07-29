package project.group1.commutemate.model;

public enum Role {
    DRIVER,
    RIDER;

    public static Role from(String value) {
        if (value == null) {
            return RIDER;
        }

        return switch (value.trim().toUpperCase()) {
            case "DRIVER" -> DRIVER;
            case "RIDER" -> RIDER;
            default -> RIDER;
        };
    }


    /** Lower-case label used in the UI, e.g. "driver". */
    public String label() {
        return name().toLowerCase();
    }
}
