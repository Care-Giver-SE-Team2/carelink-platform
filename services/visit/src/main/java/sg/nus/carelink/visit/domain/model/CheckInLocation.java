package sg.nus.carelink.visit.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** A manual note is an assertion by the caregiver, never a verified GPS fix. */
public record CheckInLocation(String source, BigDecimal latitude, BigDecimal longitude, Double accuracy,
        String note, Instant clientCapturedAt) {
    public CheckInLocation {
        latitude = latitude == null ? null : latitude.stripTrailingZeros();
        longitude = longitude == null ? null : longitude.stripTrailingZeros();
        note = note == null ? null : note.strip();
        if (!valid(source, latitude, longitude, accuracy, note)) throw new IllegalArgumentException("Invalid check-in location");
    }
    public static boolean valid(String source, BigDecimal lat, BigDecimal lon, Double accuracy, String note) {
        if ("GPS".equals(source)) return lat != null && lon != null && accuracy != null && Double.isFinite(accuracy) && accuracy >= 0
                && lat.abs().compareTo(BigDecimal.valueOf(90)) <= 0 && lon.abs().compareTo(BigDecimal.valueOf(180)) <= 0
                && (note == null || note.isBlank());
        if ("MANUAL_LOCATION_NOTE".equals(source)) return lat == null && lon == null && accuracy == null && note != null && !note.isBlank() && note.strip().length() <= 500;
        return false;
    }
}
