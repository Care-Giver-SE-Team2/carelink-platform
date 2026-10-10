package sg.nus.carelink.visit.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** One append-only set of measured readings and the observation made with it. */
public record HealthRecord(Long id, Long visitId, HealthObservation.Flag healthFlag,
        String healthNote, LocalDateTime recordedAt, List<Reading> readings) {
    public HealthRecord { readings = List.copyOf(readings); }
    public record Reading(String metric, BigDecimal value, String unit) {}
}
