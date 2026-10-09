package sg.nus.carelink.profile.application;

import java.time.LocalDate;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding.AccessScope;

/** Explicit family projection: never serializes an elder's account or internal care fields. */
public record FamilyElderProfile(Long id, String fullName, Elder.Gender gender, LocalDate dateOfBirth,
        String phone, String address, String postalCode, String preferredDialects,
        Boolean livesAlone, Elder.MobilityLevel mobilityLevel, AccessScope accessScope) {

    static FamilyElderProfile from(Elder elder, AccessScope scope) {
        return new FamilyElderProfile(elder.id(), elder.fullName(), elder.gender(), elder.dateOfBirth(),
                elder.phone(), elder.address(), elder.postalCode(), elder.preferredDialects(),
                elder.livesAlone(), elder.mobilityLevel(), scope);
    }
}
