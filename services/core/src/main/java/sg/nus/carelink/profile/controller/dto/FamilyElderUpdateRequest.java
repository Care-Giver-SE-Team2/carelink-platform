package sg.nus.carelink.profile.controller.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.ElderBasicDetails;
import sg.nus.carelink.shared.validation.OnOrAfter;
import sg.nus.carelink.shared.validation.SingaporeFormats;

/** Full replacement of family-editable details only; null optional fields clear their values. */
public record FamilyElderUpdateRequest(
        @NotBlank @CodePointLength(max = 100)
        @Pattern(regexp = SingaporeFormats.PERSON_NAME, message = SingaporeFormats.PERSON_NAME_MESSAGE) String fullName,
        Elder.Gender gender,
        @PastOrPresent(message = "Enter a date that is not in the future")
        @OnOrAfter(value = "1900-01-01", message = "Enter a date from 1900 onwards") LocalDate dateOfBirth,
        @Pattern(regexp = SingaporeFormats.PHONE, message = SingaporeFormats.PHONE_MESSAGE) String phone,
        @CodePointLength(max = 255) String address,
        @Pattern(regexp = SingaporeFormats.POSTAL_CODE, message = SingaporeFormats.POSTAL_CODE_MESSAGE) String postalCode,
        @CodePointLength(max = 100)
        @Pattern(regexp = SingaporeFormats.DIALECT_LIST, message = SingaporeFormats.DIALECT_LIST_MESSAGE) String preferredDialects,
        Boolean livesAlone,
        Elder.MobilityLevel mobilityLevel) {

    public FamilyElderUpdateRequest {
        fullName = SingaporeFormats.normalizeName(fullName);
        phone = SingaporeFormats.normalizePhone(phone);
        address = clean(address);
        postalCode = clean(postalCode);
        preferredDialects = SingaporeFormats.normalizeDialects(preferredDialects);
    }

    public ElderBasicDetails toDetails() {
        return new ElderBasicDetails(fullName, gender, dateOfBirth, phone, address, postalCode,
                preferredDialects, livesAlone, mobilityLevel);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
