package sg.nus.carelink.visit.controller.dto;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.*;
import sg.nus.carelink.visit.domain.model.HealthMeasurement;
import sg.nus.carelink.visit.domain.model.HealthObservation;

public record CaregiverHealthRequest(@NotNull UUID clientRequestId, @NotNull @PositiveOrZero Integer expectedVersion,
        @DecimalMin("0.01") @Digits(integer=6,fraction=0) BigDecimal systolic,
        @DecimalMin("0.01") @Digits(integer=6,fraction=0) BigDecimal diastolic,
        @DecimalMin("0.01") @Digits(integer=6,fraction=0) BigDecimal pulse,
        @DecimalMin("0.01") @Digits(integer=6,fraction=2) BigDecimal temperature,
        @NotNull HealthObservation.Flag healthFlag, @Size(max=1000) String healthNote) {
    public HealthMeasurement measurement() { return new HealthMeasurement(systolic, diastolic, pulse, temperature, healthFlag, healthNote); }
    @AssertTrue(message="Supply paired blood pressure values, select a health observation, and explain concerns or missing measurements")
    public boolean isValidMeasurement() {
        try { measurement(); return true; } catch (IllegalArgumentException _) { return false; }
    }
}
