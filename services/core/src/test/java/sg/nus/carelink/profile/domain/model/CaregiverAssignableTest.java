package sg.nus.carelink.profile.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CaregiverAssignableTest {

	@ParameterizedTest
	@CsvSource({ "AVAILABLE,true", "BUSY,true", "ONBOARDING,false", "INACTIVE,false" })
	void onlyWorkingCaregiversCanBeAssigned(Caregiver.Status status, boolean assignable) {
		Caregiver caregiver = new Caregiver(1L, 2L, "Aisyah N.", null, "S31", null, status, null, null);

		assertThat(caregiver.isAssignable()).isEqualTo(assignable);
	}
}
