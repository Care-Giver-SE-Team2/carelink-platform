package sg.nus.carelink.rostering.domain.model;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

/** Explicit declarations, not assigned shifts or approved leave. */
public final class CaregiverWorkSettings {
    private CaregiverWorkSettings() { }
    public record Window(LocalTime start, LocalTime end, CaregiverAvailability.Status status) { }
    public record Day(LocalDate date, List<Window> windows) { }
    public record WeeklyWindow(DayOfWeek weekday, LocalTime start, LocalTime end) { }
    public record Preferences(List<String> serviceCodes, List<String> sectors, List<WeeklyWindow> weeklyWindows,
            Integer maxVisitsPerDay, BigDecimal maxHoursPerDay) {
        public static Preferences empty() { return new Preferences(List.of(),List.of(),List.of(),null,null); }
    }
    public record Catalog(List<String> serviceCodes, List<String> sectors, int maxVisitsPerDay,
            BigDecimal maxHoursPerDay, boolean serviceCatalogConfigured) { }
    public record Snapshot(Long caregiverId, long version, LocalDateTime updatedAt, LocalDate dateFrom,
            LocalDate dateTo, Preferences preferences, List<Day> availability, List<String> dataIssues,
            String timeZone, String uncoveredTimeMeaning, String consumptionStatus) { }
    public record Booking(Long visitId, LocalDateTime start, LocalDateTime end) { }
    public record Leave(Long id, LocalDate startDate, LocalDate endDate, String status) { }
    public record View(Snapshot settings, Catalog catalog, LocalDate today, LocalDate editableUntil,
            List<Booking> bookings, List<Leave> leave, boolean canEdit) { }
    public record State(Long caregiverId, long version, LocalDateTime updatedAt) { }
    public record Receipt(String hash, Snapshot snapshot) { }
}
