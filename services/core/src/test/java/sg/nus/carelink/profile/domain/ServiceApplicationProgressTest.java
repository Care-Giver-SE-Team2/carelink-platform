package sg.nus.carelink.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.NeedProgress;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.Outcome;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.PlanVersion;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

class ServiceApplicationProgressTest {

	private static final LocalDate APPLIED = LocalDate.of(2026, 10, 9);

	private static PlanVersion version(int n, LocalDate from, LocalDate until, String... codes) {
		return new PlanVersion(n, from, until, Set.of(codes));
	}

	@Test
	void isPlannedOnceEveryActivityIsInAVersionFromTheDayTheyApplied() {
		var progress = ServiceApplicationProgress.of(List.of("BATHING", "VITALS"), APPLIED, false, List.of(
				version(2, LocalDate.of(2026, 10, 20), null, "BATHING", "VITALS")));

		assertThat(progress.outcome()).isEqualTo(Outcome.PLANNED);
		assertThat(progress.needs()).containsExactly(
				new NeedProgress("BATHING", 2, LocalDate.of(2026, 10, 20)),
				new NeedProgress("VITALS", 2, LocalDate.of(2026, 10, 20)));
	}

	@Test
	void staysSubmittedWhileAnyActivityIsMissing() {
		var progress = ServiceApplicationProgress.of(List.of("BATHING", "MEAL_SUPPORT"), APPLIED, false, List.of(
				version(2, LocalDate.of(2026, 10, 20), null, "BATHING")));

		assertThat(progress.outcome()).isEqualTo(Outcome.SUBMITTED);
		assertThat(progress.needs().get(1).planned()).isFalse();
	}

	@Test
	void anActivityAlreadyInThePlanInForceCountsStraightAway() {
		var progress = ServiceApplicationProgress.of(List.of("BATHING"), APPLIED, false, List.of(
				version(1, LocalDate.of(2026, 9, 1), null, "BATHING")));

		assertThat(progress.outcome()).isEqualTo(Outcome.PLANNED);
		assertThat(progress.needs().getFirst().plannedVersion()).isEqualTo(1);
	}

	@Test
	void aVersionThatEndedBeforeTheyAppliedDoesNotCount() {
		var progress = ServiceApplicationProgress.of(List.of("BATHING"), APPLIED, false, List.of(
				version(1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), "BATHING"),
				version(2, LocalDate.of(2026, 10, 1), null, "GROOMING")));

		assertThat(progress.outcome()).isEqualTo(Outcome.SUBMITTED);
	}

	@Test
	void staysPlannedAfterALaterVersionDropsTheActivity() {
		var progress = ServiceApplicationProgress.of(List.of("BATHING"), APPLIED, false, List.of(
				version(2, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 3), "BATHING"),
				version(3, LocalDate.of(2026, 11, 3), null, "GROOMING")));

		assertThat(progress.outcome()).isEqualTo(Outcome.PLANNED);
		assertThat(progress.needs().getFirst().plannedVersion()).isEqualTo(2);
	}

	@Test
	void aVersionReplacedBeforeItStartedNeverCounts() {
		var progress = ServiceApplicationProgress.of(List.of("BATHING"), APPLIED, false, List.of(
				version(2, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), "BATHING")));

		assertThat(progress.outcome()).isEqualTo(Outcome.SUBMITTED);
	}

	@Test
	void isDeclinedUnlessItWasPlannedAnyway() {
		assertThat(ServiceApplicationProgress.of(List.of("BATHING"), APPLIED, true, List.of()).outcome())
				.isEqualTo(Outcome.DECLINED);
		assertThat(ServiceApplicationProgress.of(List.of("BATHING"), APPLIED, true, List.of(
				version(2, LocalDate.of(2026, 10, 20), null, "BATHING"))).outcome())
				.isEqualTo(Outcome.PLANNED);
	}

	@Test
	void freeTextNeedsAreNeverPlanned() {
		var progress = ServiceApplicationProgress.of(List.of("BATHING", "Help with the cat"), APPLIED, false, List.of(
				version(2, LocalDate.of(2026, 10, 20), null, "BATHING")));

		assertThat(progress.outcome()).isEqualTo(Outcome.SUBMITTED);
	}

	@Test
	void aPlannedApplicationCannotBeDeclined() {
		var planned = ServiceApplicationProgress.of(List.of("BATHING"), APPLIED, false, List.of(
				version(2, LocalDate.of(2026, 10, 20), null, "BATHING")));

		assertThatThrownBy(() -> ServiceApplicationProgress.requireDeclinable(planned))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("SERVICE_APPLICATION_ALREADY_PLANNED");
		ServiceApplicationProgress.requireDeclinable(
				ServiceApplicationProgress.of(List.of("BATHING"), APPLIED, false, List.of()));
	}
}
