package sg.nus.carelink.profile.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class FamilyElderUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static FamilyElderUpdateRequest request(String fullName, LocalDate dateOfBirth, String phone,
            String postalCode, String dialects) {
        return new FamilyElderUpdateRequest(fullName, null, dateOfBirth, phone, "Blk 123 Ang Mo Kio Ave 6",
                postalCode, dialects, null, null);
    }

    private Set<String> invalidFields(FamilyElderUpdateRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void acceptsAndNormalizesWhatAFamilyTypes() {
        var request = request("  Tan   Ah Kow ", LocalDate.of(1948, 2, 3), "6123 4567", "560123", "hokkien, mandarin");

        assertThat(invalidFields(request)).isEmpty();
        assertThat(request.fullName()).isEqualTo("Tan Ah Kow");
        assertThat(request.phone()).isEqualTo("+6561234567");
        assertThat(request.preferredDialects()).isEqualTo("Hokkien,Mandarin");
    }

    @Test
    void leavesOptionalDetailsEmpty() {
        assertThat(invalidFields(request("Tan Ah Kow", null, " ", null, ""))).isEmpty();
    }

    @Test
    void reportsEachDetailThatIsNotASingaporeValue() {
        var request = request("Tan 2", LocalDate.of(1850, 1, 1), "1234 5678", "741234", "Hokkien,Klingon");

        assertThat(invalidFields(request))
                .containsExactlyInAnyOrder("fullName", "dateOfBirth", "phone", "postalCode", "preferredDialects");
    }

    @Test
    void rejectsADateOfBirthInTheFuture() {
        assertThat(invalidFields(request("Tan Ah Kow", LocalDate.now().plusDays(1), null, null, null)))
                .containsExactly("dateOfBirth");
    }
}
