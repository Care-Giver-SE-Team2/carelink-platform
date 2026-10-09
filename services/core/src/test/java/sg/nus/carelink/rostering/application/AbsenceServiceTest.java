package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/** Absences themselves: recorded by a manager, asked for by a caregiver, reviewed once, never twice on a day. */
class AbsenceServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	private final ReRosteringFakes.Absences absences = new ReRosteringFakes.Absences();
	private final ReRosteringFakes.Profiles profiles = new ReRosteringFakes.Profiles().caregiver(5L, "Aisha", false);
	private final CaregiverWorkDirectory caregivers = mock(CaregiverWorkDirectory.class);
	private final List<String> told = new ArrayList<>();
	private final AbsenceService service = new AbsenceService(absences, profiles, caregivers,
			new ReRosteringFakes.AbsenceAlerts(told),
			new ReRosteringFakes.MutableClock(TODAY.atTime(9, 0), ZoneId.of("Asia/Singapore")));

	@Test
	void aManagerRecordsAnAbsenceForAKnownCaregiver() {
		AbsenceReport recorded = service.recordForCaregiver(5L, AbsenceReport.Type.SICK, TODAY, TODAY.plusDays(1),
				"flu", 11L);

		assertThat(recorded.id()).isNotNull();
		assertThat(recorded.status()).isEqualTo(AbsenceReport.Status.APPROVED);
		assertThat(service.require(recorded.id())).isEqualTo(recorded);
		assertThatThrownBy(() -> service.recordForCaregiver(404L, null, TODAY, TODAY, null, 11L))
				.isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void twoAbsencesMayNotShareADayUnlessOneWasTurnedDown() {
		AbsenceReport first = service.recordForCaregiver(5L, AbsenceReport.Type.SICK, TODAY, TODAY.plusDays(2), null, 11L);

		LocalDate lastDayOfFirst = TODAY.plusDays(2);
		LocalDate dayAfter = TODAY.plusDays(3);
		assertThatThrownBy(() -> service.recordForCaregiver(5L, AbsenceReport.Type.SICK, lastDayOfFirst, dayAfter,
				null, 11L))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("2026-10-07 to 2026-10-09")
				.extracting("code").isEqualTo("ABSENCE_OVERLAPS");

		when(caregivers.require("aisha")).thenReturn(profile());
		AbsenceReport asked = service.requestForSelf("aisha", AbsenceReport.Type.ANNUAL, TODAY.plusDays(5),
				TODAY.plusDays(6), "trip");
		service.reject(asked.id(), 11L);
		AbsenceReport again = service.requestForSelf("aisha", AbsenceReport.Type.ANNUAL, TODAY.plusDays(5),
				TODAY.plusDays(6), "trip, second try");
		assertThat(again.id()).isNotEqualTo(asked.id());
		assertThat(first.id()).isNotNull();
	}

	@Test
	void aCaregiversRequestIsReviewedOnce() {
		when(caregivers.require("aisha")).thenReturn(profile());

		AbsenceReport asked = service.requestForSelf("aisha", null, TODAY.plusDays(1), TODAY.plusDays(1), null);
		assertThat(asked.status()).isEqualTo(AbsenceReport.Status.PENDING);
		assertThat(asked.caregiverId()).isEqualTo(5L);
		assertThat(told).as("the managers hear of a request, not of an absence they recorded themselves")
				.containsExactly("Aisha " + asked.id());

		AbsenceReport approved = service.approve(asked.id(), 11L);
		assertThat(approved.status()).isEqualTo(AbsenceReport.Status.APPROVED);
		assertThatThrownBy(() -> service.reject(asked.id(), 11L))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("ABSENCE_ALREADY_REVIEWED");
		assertThat(service.listForSelf("aisha")).extracting(AbsenceReport::id).containsExactly(asked.id());
		assertThat(service.list(AbsenceReport.Status.APPROVED)).hasSize(1);
		assertThat(service.list(AbsenceReport.Status.PENDING)).isEmpty();
		assertThat(service.list(null)).hasSize(1);
		assertThatThrownBy(() -> service.approve(404L, 11L)).isInstanceOf(ResourceNotFound.class);
	}

	private static CaregiverWorkDirectory.Profile profile() {
		return new CaregiverWorkDirectory.Profile(5L, 1005L, "Aisha", null, null, null, "AVAILABLE");
	}
}
