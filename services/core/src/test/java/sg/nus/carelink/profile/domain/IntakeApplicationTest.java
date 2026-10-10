package sg.nus.carelink.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.IntakeApplication;

/**
 * Verifies the application keeps an immutable snapshot of what the family asked for.
 *
 * @author Wang Zhili
 */
class IntakeApplicationTest {

	@Test
	void keepsCareNeedsAsAnImmutableSnapshot() {
		var careNeeds = new ArrayList<>(List.of("BATHING", "VITALS"));
		IntakeApplication application = application(careNeeds);

		careNeeds.clear();

		assertThat(application.careNeeds()).containsExactly("BATHING", "VITALS");
		assertThatThrownBy(() -> application.careNeeds().add("OTHER"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void treatsMissingCareNeedsAsNone() {
		assertThat(application(null).careNeeds()).isEmpty();
	}

	private static IntakeApplication application(List<String> careNeeds) {
		return new IntakeApplication(1L, 42L, "Tan Mei", 80, "12 Example Road", "123456",
				IntakeApplication.MobilityLevel.ASSISTIVE_CANE, "Hokkien", careNeeds, null,
				IntakeApplication.Status.APPROVED, 7L, null, null, null, 301L);
	}
}
