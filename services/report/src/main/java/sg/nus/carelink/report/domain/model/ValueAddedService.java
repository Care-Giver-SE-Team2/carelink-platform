package sg.nus.carelink.report.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** Catalogue entry for an optional service that an elder may request (UC-EL02). */
public record ValueAddedService(
        Long id,
        String name,
        String description,
        int durationMinutes,
        Status status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** What a service is taken to last when the catalogue says nothing else. */
    public static final int DEFAULT_DURATION_MINUTES = 60;
    /** The longest a service may last: one visit, within a caregiver's day. */
    public static final int MAX_DURATION_MINUTES = 720;
    /** As long as value_added_service.name. */
    public static final int MAX_NAME_LENGTH = 100;

    public ValueAddedService {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("A value-added service must last some time: " + durationMinutes);
        }
    }

    public ValueAddedService(Long id, String name, String description, Status status,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, name, description, DEFAULT_DURATION_MINUTES, status, createdAt, updatedAt);
    }

    /**
     * A new service a manager adds to the catalogue, available at once. Its name is kept as given, stripped:
     * providers offer services by name, so it never changes afterwards (docs/section5/decisions.md, D21).
     */
    public static ValueAddedService added(String name, String description, int durationMinutes) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A value-added service needs a name of 1 to " + MAX_NAME_LENGTH
                    + " characters");
        }
        return new ValueAddedService(null, stripped, blankToNull(description), checkedDuration(durationMinutes),
                Status.AVAILABLE, null, null);
    }

    /**
     * The same service with a new description, length or status. A new length is for the visits booked from now
     * on; one already booked keeps its times. Withdrawing it stops new requests; those made already carry on.
     */
    public ValueAddedService revised(String nextDescription, int nextDurationMinutes, Status nextStatus) {
        return new ValueAddedService(id, name, blankToNull(nextDescription), checkedDuration(nextDurationMinutes),
                Objects.requireNonNull(nextStatus, "status"), createdAt, updatedAt);
    }

    private static int checkedDuration(int minutes) {
        if (minutes <= 0 || minutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("A value-added service lasts 1 to " + MAX_DURATION_MINUTES
                    + " minutes: " + minutes);
        }
        return minutes;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** How long the visit an approved request dispatches lasts. */
    public Duration duration() {
        return Duration.ofMinutes(durationMinutes);
    }

    public boolean available() {
        return status == Status.AVAILABLE;
    }

    public enum Status {
        AVAILABLE, UNAVAILABLE
    }
}
