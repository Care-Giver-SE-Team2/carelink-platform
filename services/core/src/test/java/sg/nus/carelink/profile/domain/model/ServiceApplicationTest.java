package sg.nus.carelink.profile.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

class ServiceApplicationTest {
    private Elder elder(String name, String address, String postal) {
        return new Elder(1L, 71L, name, null, null, null, address, postal, "Internal sector", null, null,
                null, Elder.ContinuityPreference.REQUIRED, "Private medical notes", null, null);
    }

    @Test
    void capturesSavedBasicDetailsAndNormalizesServicesAndNotes() {
        var needs = new ArrayList<>(List.of(" BATHING ", "BATHING", "Help with meals"));
        var now = LocalDateTime.of(2026, 10, 9, 6, 0);
        var result = ServiceApplication.submit(42L, elder(" Tan Mei ", "12 Example Road", "123456"), needs, " Notes ", now);
        needs.clear();
        assertThat(result.applicantFamilyMemberId()).isEqualTo(42L);
        assertThat(result.elderId()).isEqualTo(1L);
        assertThat(result.elderSnapshot().fullName()).isEqualTo("Tan Mei");
        assertThat(result.elderSnapshot().address()).isEqualTo("12 Example Road");
        assertThat(result.elderSnapshot().mobilityLevel()).isNull();
        assertThat(result.careNeeds()).containsExactly("BATHING", "Help with meals");
        assertThat(result.notes()).isEqualTo("Notes");
        assertThat(result.status()).isEqualTo(ServiceApplication.Status.SUBMITTED);
        assertThat(result.createdAt()).isEqualTo(now);
        assertThatThrownBy(() -> result.careNeeds().add("Other")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL|Road|123456", " |Road|123456", "Tan Mei|NULL|123456", "Tan Mei| |123456",
            "Tan Mei|Road|NULL", "Tan Mei|Road|12345", "Tan Mei|Road|ABCDEF"}, delimiter = '|', nullValues = "NULL")
    void incompleteSavedProfileRequiresCompletion(String name, String address, String postal) {
        assertThatThrownBy(() -> ServiceApplication.submit(42L, elder(name, address, postal), List.of("BATHING"), null,
                LocalDateTime.now())).isInstanceOfSatisfying(BusinessRuleViolation.class,
                        error -> assertThat(error.code()).isEqualTo("ELDER_PROFILE_INCOMPLETE"));
    }

    @Test
    void missingServiceNeedsAreRejected() {
        for (List<String> needs : java.util.Arrays.asList(null, List.<String>of(), List.of(" "), java.util.Arrays.asList((String) null))) {
            assertThatThrownBy(() -> ServiceApplication.submit(42L, elder("Tan Mei", "Road", "123456"), needs, null,
                    LocalDateTime.now())).isInstanceOfSatisfying(BusinessRuleViolation.class,
                            error -> assertThat(error.code()).isEqualTo("CARE_NEEDS_REQUIRED"));
        }
    }

    @Test
    void optionalNotesCanBeOmittedOrBlank() {
        for (String notes : java.util.Arrays.asList(null, " ")) {
            assertThat(ServiceApplication.submit(42L, elder("Tan Mei", "Road", "123456"), List.of("VITALS"), notes,
                    LocalDateTime.now()).notes()).isNull();
        }
    }
}
