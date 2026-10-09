package sg.nus.carelink.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.model.IntakeSubmission;

/**
 * Verifies required intake details, default values and initial application state.
 *
 * @author Wang Zhili
 */
class IntakeApplicationTest {

	@Test
	void submitsAnApplicationWithoutApprovingOrCreatingAnElder() {
		var details = new IntakeSubmission("  Tan Mei  ", null, "  12 Example Road  ",
				" 123456 ", null, null, null, null);

		IntakeApplication application = IntakeApplication.submit(42L, details);

		assertThat(application.applicantFamilyMemberId()).isEqualTo(42L);
		assertThat(application.targetElderName()).isEqualTo("Tan Mei");
		assertThat(application.targetAddress()).isEqualTo("12 Example Road");
		assertThat(application.postalCode()).isEqualTo("123456");
		assertThat(application.mobilityLevel()).isEqualTo(IntakeApplication.MobilityLevel.INDEPENDENT);
		assertThat(application.careNeeds()).isEmpty();
		assertThat(application.status()).isEqualTo(IntakeApplication.Status.SUBMITTED);
		assertThat(application.id()).isNull();
		assertThat(application.createdAt()).isNull(); // Assigned by the database when saved.
		assertThat(application.reviewedByUserId()).isNull();
		assertThat(application.reviewRemarks()).isNull();
		assertThat(application.reviewedAt()).isNull();
		assertThat(application.elderId()).isNull();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = { 0L, -1L })
	void requiresAPositiveFamilyProfileIdentifier(Long familyId) {
		var details = new IntakeSubmission("Tan Mei", null, "12 Example Road", "123456", null, null, null, null);

		assertThatIllegalArgumentException().isThrownBy(() -> IntakeApplication.submit(familyId, details));
	}

	@ParameterizedTest
	@MethodSource("missingRequiredDetails")
	void refusesMissingOrBlankRequiredDetails(String name, String address, String postalCode) {
		assertThatIllegalArgumentException().isThrownBy(() ->
				new IntakeSubmission(name, null, address, postalCode, null, null, null, null));
	}

	private static Stream<Arguments> missingRequiredDetails() {
		return Stream.of(
				Arguments.of(null, "12 Example Road", "123456"),
				Arguments.of(" \t\n ", "12 Example Road", "123456"),
				Arguments.of("Tan Mei", null, "123456"),
				Arguments.of("Tan Mei", " \t ", "123456"),
				Arguments.of("Tan Mei", "12 Example Road", null),
				Arguments.of("Tan Mei", "12 Example Road", " \n "));
	}

	@Test
	void preservesOptionalDetailsAsAnImmutableSubmissionSnapshot() {
		var careNeeds = new ArrayList<>(List.of("BATHING", "VITALS"));
		var details = new IntakeSubmission("Tan Mei", 80, "12 Example Road", "123456",
				IntakeApplication.MobilityLevel.ASSISTIVE_CANE, "Hokkien", careNeeds, "Needs assistance");
		IntakeApplication application = IntakeApplication.submit(42L, details);

		careNeeds.clear();

		assertThat(application.targetElderAge()).isEqualTo(80);
		assertThat(application.mobilityLevel()).isEqualTo(IntakeApplication.MobilityLevel.ASSISTIVE_CANE);
		assertThat(application.preferredDialects()).isEqualTo("Hokkien");
		assertThat(application.medicalNotes()).isEqualTo("Needs assistance");
		assertThat(application.careNeeds()).containsExactly("BATHING", "VITALS");
		assertThatThrownBy(() -> application.careNeeds().add("OTHER"))
				.isInstanceOf(UnsupportedOperationException.class);
	}
}
