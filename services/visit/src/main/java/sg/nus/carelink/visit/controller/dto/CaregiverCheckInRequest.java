package sg.nus.carelink.visit.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.*;
import sg.nus.carelink.visit.domain.model.CheckInLocation;

public record CaregiverCheckInRequest(@NotNull UUID clientRequestId, @NotNull @PositiveOrZero Integer expectedVersion,
        @NotNull @Pattern(regexp="GPS|MANUAL_LOCATION_NOTE") String locationSource,
        BigDecimal latitude, BigDecimal longitude, Double accuracy, @Size(max=500) String locationNote, Instant clientCapturedAt) {
    @AssertTrue(message="Provide a valid GPS fix or a clearly marked manual location note, not both")
    public boolean isValidLocation() { return CheckInLocation.valid(locationSource,latitude,longitude,accuracy,locationNote); }
    public CheckInLocation location() { return new CheckInLocation(locationSource,latitude,longitude,accuracy,locationNote,clientCapturedAt); }
}
