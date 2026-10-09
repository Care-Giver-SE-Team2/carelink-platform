package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

class PrimaryCaregiverServiceTest {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 10, 30);

	private final InMemoryElderRepository elders = new InMemoryElderRepository();
	private final InMemoryCaregiverRepository caregivers = new InMemoryCaregiverRepository();
	private final InMemoryPrimaryCaregiverAssignmentRepository assignments =
			new InMemoryPrimaryCaregiverAssignmentRepository();
	private final List<Object> events = new ArrayList<>();
	private final PrimaryCaregiverService service = new PrimaryCaregiverService(elders, caregivers, assignments,
			Clock.fixed(NOW.atZone(SINGAPORE).toInstant(), SINGAPORE), events::add);

	@Test
	void assignsAndStampsTheTime() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);

		PrimaryCaregiverSummary summary = service.assign(elder.id(), aisyah.id());

		assertThat(summary).isEqualTo(new PrimaryCaregiverSummary(aisyah.id(), "Aisyah N.", NOW));
		assertThat(assignments.findByElderId(elder.id()))
				.contains(new PrimaryCaregiverAssignment(elder.id(), aisyah.id(), NOW));
	}

	@Test
	void reassigningReplacesTheExistingCaregiver() {
		Elder elder = saveElder();
		Caregiver first = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		Caregiver second = caregivers.save("Devi Raman", Caregiver.Status.BUSY);

		service.assign(elder.id(), first.id());
		service.assign(elder.id(), second.id());

		assertThat(assignments.findByElderId(elder.id())).get()
				.extracting(PrimaryCaregiverAssignment::caregiverId).isEqualTo(second.id());
	}

	@Test
	void refusesACaregiverWhoIsNotAssignable() {
		Elder elder = saveElder();
		Caregiver onboarding = caregivers.save("New Hire", Caregiver.Status.ONBOARDING);

		assertThatThrownBy(() -> service.assign(elder.id(), onboarding.id()))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(e -> ((BusinessRuleViolation) e).code()).isEqualTo("CAREGIVER_NOT_ASSIGNABLE");
		assertThat(assignments.findByElderId(elder.id())).isEmpty();
	}

	@Test
	void rejectsAnUnknownElderOrCaregiver() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);

		assertThatThrownBy(() -> service.assign(999L, aisyah.id())).isInstanceOf(ResourceNotFound.class);
		assertThatThrownBy(() -> service.assign(elder.id(), 999L)).isInstanceOf(ResourceNotFound.class);
		assertThatThrownBy(() -> service.unassign(999L)).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void unassignRemovesTheAssignmentAndIsIdempotent() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		service.assign(elder.id(), aisyah.id());

		service.unassign(elder.id());
		service.unassign(elder.id());

		assertThat(assignments.findByElderId(elder.id())).isEmpty();
	}

	@Test
	void listsEveryCaregiverByName() {
		caregivers.save("Wei Jie Tan", Caregiver.Status.AVAILABLE);
		caregivers.save("Aisyah N.", Caregiver.Status.INACTIVE);

		assertThat(service.listCaregivers()).extracting(Caregiver::fullName)
				.containsExactly("Aisyah N.", "Wei Jie Tan");
	}

	@Test
	void announcesTheNewPrimaryCaregiverSoTheirVisitsCanFollow() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);

		service.assign(elder.id(), aisyah.id());

		assertThat(events).containsExactly(new PrimaryCaregiverChanged(elder.id()));
	}

	@Test
	void rostersVisitsToTheNamedCaregiverWhileTheyCanTakeThem() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.BUSY);
		service.assign(elder.id(), aisyah.id());

		assertThat(service.findRosterableCaregiverId(elder.id())).contains(aisyah.id());
	}

	@Test
	void rostersToNobodyWhenThereIsNoPrimaryCaregiverOrTheyHaveGoneInactive() {
		Elder unassigned = saveElder();
		Elder elder = saveElder();
		Caregiver inactive = caregivers.save("Left Last Month", Caregiver.Status.INACTIVE);
		assignments.save(new PrimaryCaregiverAssignment(elder.id(), inactive.id(), NOW));

		assertThat(service.findRosterableCaregiverId(unassigned.id())).isEmpty();
		assertThat(service.findRosterableCaregiverId(elder.id())).isEmpty();
	}

	private Elder saveElder() {
		return elders.save(new Elder(null, null, "Chan Bee Choo", null, null, null, null, null, "S31", null,
				null, null, Elder.ContinuityPreference.PREFERRED, null, null, null));
	}
}
