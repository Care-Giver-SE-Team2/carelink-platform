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
