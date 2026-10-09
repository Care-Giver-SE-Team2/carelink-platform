package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.application.CredentialRegister;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * A manager staffs one visit nobody holds - an approved extra service whose elder's primary
 * caregiver was not free. The same hard rules as an absence's search decide who may go, both
 * when the picker is drawn and again when the manager picks.
 */
class VisitCoverServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 0, 0);
	private static final LocalDateTime TENTH = LocalDateTime.of(2026, 10, 10, 10, 0);

	private final ReRosteringFakes.MutableClock clock = new ReRosteringFakes.MutableClock(NOW, ZONE);
	private final ReRosteringFakes.Absences absences = new ReRosteringFakes.Absences();
	private final ReRosteringFakes.Visits visits = new ReRosteringFakes.Visits();
	private final ReRosteringFakes.Profiles profiles = new ReRosteringFakes.Profiles();
	private final List<CredentialRegister.Cover> covers = new ArrayList<>();

	private VisitCoverService service;

	@BeforeEach
	void setUp() {
		profiles.caregiver(5L, "Aisha", false).caregiver(9L, "Farah", false).caregiver(10L, "Siti", true)
				.elder(7L, "Mdm Tan").elder(8L, "Mr Ong");
		for (Long caregiver : List.of(5L, 9L, 10L)) {
			covers.add(new CredentialRegister.Cover(caregiver, caregiver, 2L, LocalDate.of(2027, 6, 30)));
		}
		// The extra service's visit, which nobody holds; Aisha is busy with Mr Ong at the same hour.
		visits.add(40L, 7L, null, TENTH, 60).add(41L, 8L, 5L, TENTH, 60);

		RosterSnapshotLoader loader = new RosterSnapshotLoader(profiles, () -> covers,
				planIds -> Map.of(4L, Set.of(2L)), absences, visits, since -> Map.of(), RosterLookbacks.DEFAULT, clock);
		service = new VisitCoverService(visits, loader, new ReRosteringFakes.Constraints(),
				new ReRosteringFakes.Candidates(), new ReRosteringFakes.Checks());
	}

	@Test
	void optionsListWhoCanGoFirstThenWhoCannotAndWhy() {
		List<VisitCover.Option> options = service.options(40L);

		assertThat(options).extracting(VisitCover.Option::caregiverId).containsExactly(9L, 5L, 10L);
		assertThat(options.get(0).rank()).isEqualTo(1);
		assertThat(options.get(0).eligible()).isTrue();
		assertThat(options.get(1).eligible()).as("Aisha is with Mr Ong then").isFalse();
		assertThat(options.get(1).reason()).isNotBlank();
		assertThat(options.get(2).eligible()).as("Siti is still onboarding").isFalse();
	}

	@Test
	void coverPutsTheChosenCaregiverOnTheVisit() {
		service.cover(40L, 9L, 11L);

		assertThat(visits.rows.get(40L).caregiverId()).isEqualTo(9L);
		assertThat(visits.calls).containsExactly("reassign 40 to 9");
	}

	@Test
	void aCaregiverAHardRuleExcludesIsRefusedWithTheReason() {
		assertThatThrownBy(() -> service.cover(40L, 5L, 11L))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageStartingWith("This caregiver cannot take the visit: ");
		assertThat(visits.rows.get(40L).caregiverId()).isNull();
	}

	@Test
	void aVisitSomebodyAlreadyHoldsIsNotOpenToCover() {
		assertThatThrownBy(() -> service.options(41L))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("already has a caregiver");
	}

	@Test
	void aVisitPastSchedulingIsNotOpenToCover() {
		visits.setStatus(40L, "EXCEPTION");

		assertThatThrownBy(() -> service.cover(40L, 9L, 11L))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("EXCEPTION");
	}

	@Test
	void anUnknownVisitIsNotFound() {
		assertThatThrownBy(() -> service.options(999L)).isInstanceOf(ResourceNotFound.class);
	}
}
