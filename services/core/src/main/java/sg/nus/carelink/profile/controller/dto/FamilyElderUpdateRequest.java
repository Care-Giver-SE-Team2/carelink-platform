package sg.nus.carelink.profile.controller.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.ElderBasicDetails;

/** Full replacement of family-editable details only; null optional fields clear their values. */
public record FamilyElderUpdateRequest(
        @NotBlank @CodePointLength(max = 100) String fullName,
        Elder.Gender gender,
        @PastOrPresent LocalDate dateOfBirth,
        @CodePointLength(max = 20) String phone,
        @CodePointLength(max = 255) String address,
        @Pattern(regexp = "[0-9]{6}") String postalCode,
        @CodePointLength(max = 100) String preferredDialects,
        Boolean livesAlone,
        Elder.MobilityLevel mobilityLevel) {

    public FamilyElderUpdateRequest {
        fullName = clean(fullName);
        phone = clean(phone);
        address = clean(address);
        postalCode = clean(postalCode);
        preferredDialects = clean(preferredDialects);
    }

    public ElderBasicDetails toDetails() {
        return new ElderBasicDetails(fullName, gender, dateOfBirth, phone, address, postalCode,
                preferredDialects, livesAlone, mobilityLevel);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
