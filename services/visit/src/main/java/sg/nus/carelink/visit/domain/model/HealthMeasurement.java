package sg.nus.carelink.visit.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Only storage/format rules; clinical thresholds are not inferred here. */
public record HealthMeasurement(BigDecimal systolic, BigDecimal diastolic, BigDecimal pulse,
        BigDecimal temperature, HealthObservation.Flag healthFlag, String healthNote) {
    public HealthMeasurement {
        systolic = number(systolic, true);
        diastolic = number(diastolic, true);
        pulse = number(pulse, true);
        temperature = number(temperature, false);
        healthNote = healthNote == null || healthNote.isBlank() ? null : healthNote.strip();
        if (healthFlag == null || (systolic == null) != (diastolic == null)) {
            throw new IllegalArgumentException("Select a health observation and supply both blood pressure values.");
        }
        boolean noReadings = systolic == null && pulse == null && temperature == null;
        if ((healthFlag != HealthObservation.Flag.NO_CONCERN || noReadings) && healthNote == null) {
            throw new IllegalArgumentException("Explain the concern or why no readings were measured.");
        }
        if (healthNote != null && healthNote.length() > 1000) {
            throw new IllegalArgumentException("Health note must not exceed 1000 characters.");
        }
    }
    private static BigDecimal number(BigDecimal value, boolean integer) {
        if (value == null) return null;
        var normalized = value.stripTrailingZeros();
        if (normalized.signum() <= 0 || normalized.compareTo(new BigDecimal("999999.99")) > 0
                || normalized.scale() > (integer ? 0 : 2)) {
            throw new IllegalArgumentException("Readings must be positive, fit DECIMAL(8,2), and use the supported precision.");
        }
        return normalized;
    }
    public List<HealthRecord.Reading> readings() {
        var readings = new ArrayList<HealthRecord.Reading>();
        add(readings, "systolic", systolic, "mmHg");
        add(readings, "diastolic", diastolic, "mmHg");
        add(readings, "pulse", pulse, "bpm");
        add(readings, "temperature", temperature, "°C");
        return List.copyOf(readings);
    }
    private static void add(List<HealthRecord.Reading> readings, String metric, BigDecimal value, String unit) {
        if (value != null) readings.add(new HealthRecord.Reading(metric, value, unit));
    }
}
