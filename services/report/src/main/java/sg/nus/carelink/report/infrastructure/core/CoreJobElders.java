package sg.nus.carelink.report.infrastructure.core;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.report.application.JobElders;
import sg.nus.carelink.report.domain.model.PostalDistricts;

/**
 * {@link JobElders} from core, with no change to core (docs/section5/decisions.md, D7): the elder's
 * caregiver view for the address and languages, and the report profile for gender, age and mobility.
 * The name, phone and medical notes are never passed on. The district comes from the postal code at the
 * end of the address; core's {@code postalSector} is its own locality band, given as the area when the
 * address has no postal code.
 */
@Component
class CoreJobElders implements JobElders {

    private final CoreApi core;
    private final Clock clock;

    CoreJobElders(CoreApi core, Clock clock) {
        this.core = core;
        this.clock = clock;
    }

    @Override
    public Brief brief(Long elderId) {
        CoreApi.ElderView view = core.elderCaregiverView(elderId);
        CoreApi.ElderReportProfile profile = core.elderReportProfile(elderId);
        String languages = view.languageNeeds() == null || view.languageNeeds().isEmpty() ? "Not stated"
                : String.join(", ", view.languageNeeds());
        return PostalDistricts.of(view.serviceAddress())
                .map(district -> new Brief(summary(profile), languages, district.number(), district.areaName()))
                .orElseGet(() -> new Brief(summary(profile), languages, "",
                        view.postalSector() == null || view.postalSector().isBlank() ? "Singapore"
                                : view.postalSector()));
    }

    @Override
    public Address address(Long elderId) {
        CoreApi.ElderView view = core.elderCaregiverView(elderId);
        return new Address(view.serviceAddress() == null ? "" : view.serviceAddress(), view.accessNotes());
    }

    private String summary(CoreApi.ElderReportProfile profile) {
        List<String> parts = new ArrayList<>();
        if (profile.gender() != null) {
            parts.add(capitalised(profile.gender()));
        }
        if (profile.dateOfBirth() != null) {
            parts.add(String.valueOf(Period.between(profile.dateOfBirth(), LocalDate.now(clock)).getYears()));
        }
        String first = parts.isEmpty() ? "Elder" : String.join(", ", parts);
        String mobility = mobility(profile.mobilityLevel());
        return mobility == null ? first : first + " · " + mobility;
    }

    private static String mobility(String level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case "INDEPENDENT" -> "walks unaided";
            case "ASSISTIVE_CANE" -> "uses a walking aid";
            case "WHEELCHAIR_BEDBOUND" -> "wheelchair or bed-bound";
            default -> null;
        };
    }

    private static String capitalised(String word) {
        String lower = word.toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
