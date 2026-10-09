package sg.nus.carelink.visit.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HealthMeasurementTest {
    private HealthMeasurement input(String systolic, String diastolic, String pulse, String temperature, HealthObservation.Flag flag, String note) {
        return new HealthMeasurement(decimal(systolic), decimal(diastolic), decimal(pulse), decimal(temperature), flag, note);
    }
    private BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
    @Test void producesFourCanonicalReadingsWithoutGuessingClinicalThresholds() {
        var measurement = input("120", "80", "72", "36.70", HealthObservation.Flag.ATTENTION, "  Factual note  ");
        assertThat(measurement.healthNote()).isEqualTo("Factual note");
        assertThat(measurement.temperature()).isEqualTo(new BigDecimal("36.7"));
        assertThat(measurement.readings()).extracting(HealthRecord.Reading::metric).containsExactly("systolic", "diastolic", "pulse", "temperature");
        assertThat(measurement.readings()).extracting(HealthRecord.Reading::unit).containsExactly("mmHg", "mmHg", "bpm", "°C");
        assertThat(input(null, null, "72", null, HealthObservation.Flag.NO_CONCERN, " ").readings()).hasSize(1);
        assertThat(input(null, null, null, null, HealthObservation.Flag.NO_CONCERN, "Not measured").readings()).isEmpty();
    }
    @Test void rejectsIncompleteBloodPressureMissingObservationAndUnexplainedConcern() {
        assertThatThrownBy(() -> input("120", null, null, null, HealthObservation.Flag.NO_CONCERN, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input(null, "80", null, null, HealthObservation.Flag.NO_CONCERN, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input(null, null, "72", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input(null, null, "72", null, HealthObservation.Flag.ATTENTION, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input(null, null, null, null, HealthObservation.Flag.NO_CONCERN, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input(null, null, "72", null, HealthObservation.Flag.MEDICAL_REVIEW, "a".repeat(1001))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsInvalidStoragePrecisionAndNonPositiveValues() {
        for (String value : new String[]{"0", "-1", "72.5", "1000000"}) {
            assertThatThrownBy(() -> input(null, null, value, null, HealthObservation.Flag.NO_CONCERN, null)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> input(null, null, null, "36.777", HealthObservation.Flag.NO_CONCERN, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input(null, null, null, "1000000", HealthObservation.Flag.NO_CONCERN, null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void observationRequiresRealCheckInSurvivesPauseAndIsNotSerializedByGenericVisitApis() {
        var now = LocalDateTime.of(2026, 10, 9, 10, 0);
        var visit = Visit.scheduled(2L, 3L, 4L, 5L, "Care", now, now.plusHours(1));
        var measured = input("120", "80", "72", "36.7", HealthObservation.Flag.ATTENTION, "Private observation");
        assertThatThrownBy(() -> visit.observedHealth(measured)).hasMessageContaining("Check in");
        var started = visit.arrivedAt(now).started().observedHealth(measured);
        assertThat(started.reportedException(now).healthFlag()).isEqualTo(HealthObservation.Flag.ATTENTION);
        assertThat(started.reportedException(now).healthNote()).isEqualTo("Private observation");
        assertThat(started.checkedOutAt()).isNull();
        assertThat(started.status()).isEqualTo(Visit.Status.IN_PROGRESS);
        assertThat(JsonMapper.builder().build().writeValueAsString(started)).doesNotContain("healthFlag", "healthNote", "Private observation");
        var corrupt = new Visit(1L, 2L, 3L, null, null, "Care", now, now.plusHours(1), null, null, Visit.Status.IN_PROGRESS, null, null, 0, now, now);
        assertThatThrownBy(() -> corrupt.observedHealth(measured)).hasMessageContaining("Check in");
    }
}
