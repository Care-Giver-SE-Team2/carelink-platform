package sg.nus.carelink.profile.domain.model;

import java.time.LocalDate;

/** Family-maintained basic details; excludes login, clinical records and care management fields. */
public record ElderBasicDetails(String fullName, Elder.Gender gender, LocalDate dateOfBirth,
        String phone, String address, String postalCode, String preferredDialects,
        Boolean livesAlone, Elder.MobilityLevel mobilityLevel) {

    public ElderBasicDetails {
        fullName = clean(fullName);
        phone = clean(phone);
        address = clean(address);
        postalCode = clean(postalCode);
        preferredDialects = clean(preferredDialects);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
