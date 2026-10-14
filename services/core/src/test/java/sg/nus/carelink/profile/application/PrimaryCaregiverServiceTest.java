package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.careplan.application.CarePlanSchedules.PlanSchedule;
import sg.nus.carelink.profile.application.CredentialRegister.Cover;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

class PrimaryCaregiverServiceTest {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 10, 30);
	private static final LocalDate TODAY = NOW.toLocalDate();
	private static final Long FIRST_AID = 3L;

	private final InMemoryElderRepository elders = new InMemoryElderRepository();
	private final InMemoryCaregiverRepository caregivers = new InMemoryCaregiverRepository();
	private final InMemoryPrimaryCaregiverAssignmentRepository assignments =
			new InMemoryPrimaryCaregiverAssignmentRepository();
	private final List<Object> events = new ArrayList<>();
	private final List<PlanSchedule> plans = new ArrayList<>();
	private final Map<Long, Set<Long>> requiredTypes = new HashMap<>();
	private final List<Cover> covers = new ArrayList<>();
	private final PrimaryCaregiverService service = new PrimaryCaregiverService(elders, caregivers, assignments,
			new CarePlanSchedules() {
				@Override
				public List<PlanSchedule> forElder(Long elderId) {
					return plans.stream().filter(plan -> plan.elderId().equals(elderId)).toList();
				}

				@Override
				public List<Long> eldersWithSchedules() {
					return plans.stream().map(PlanSchedule::elderId).distinct().toList();
				}
			},
			planIds -> planIds.stream().filter(requiredTypes::containsKey)
					.collect(java.util.stream.Collectors.toMap(id -> id, requiredTypes::get)),
			() -> covers,
			new CredentialTypeRepository() {
				@Override
				public Optional<CredentialType> findById(Long id) {
					return FIRST_AID.equals(id) ? Optional.of(new CredentialType(id, "First Aid", null)) : Optional.empty();
				}

				@Override
				public List<CredentialType> findByIds(Set<Long> ids) {
					return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
				}

				@Override
				public CredentialType save(CredentialType credentialType) {
					throw new UnsupportedOperationException();
				}
			},
			Clock.fixed(NOW.atZone(SINGAPORE).toInstant(), SINGAPORE), events::add, 30);

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

		assertThat(events).containsExactly(new PrimaryCaregiverChanged(elder.id(), null));
	}

	@Test
	void namesTheCaregiverItReplacesSoTheirVisitsCanBeReleased() {
		Elder elder = saveElder();
		Caregiver first = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		Caregiver second = caregivers.save("Devi Raman", Caregiver.Status.AVAILABLE);
		service.assign(elder.id(), first.id());
		events.clear();

		service.assign(elder.id(), second.id());
		service.assign(elder.id(), second.id());

		assertThat(events).containsExactly(new PrimaryCaregiverChanged(elder.id(), first.id()),
				new PrimaryCaregiverChanged(elder.id(), null));
	}

	@Test
	void removingTheCaregiverAnnouncesWhoWasRemovedOnlyWhenSomeoneWas() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		service.assign(elder.id(), aisyah.id());
		events.clear();

		service.unassign(elder.id());
		service.unassign(elder.id());

		assertThat(events).containsExactly(new PrimaryCaregiverChanged(elder.id(), aisyah.id()));
	}

	@Test
	void refusesACaregiverWithoutACertificateThePlanRequires() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		plans.add(plan(9L, elder.id(), TODAY.minusDays(30), null));
		requiredTypes.put(9L, Set.of(FIRST_AID));

		assertThatThrownBy(() -> service.assign(elder.id(), aisyah.id()))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("Aisyah N. has no First Aid certificate")
				.extracting(e -> ((BusinessRuleViolation) e).code()).isEqualTo("CAREGIVER_MISSING_CREDENTIAL");
		assertThat(assignments.findByElderId(elder.id())).isEmpty();
		assertThat(events).isEmpty();
	}

	@Test
	void refusesACaregiverWhoseCertificateRunsOutBeforeAnUpcomingPlanStarts() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		plans.add(plan(10L, elder.id(), TODAY.plusDays(7), null));
		requiredTypes.put(10L, Set.of(FIRST_AID));
		covers.add(new Cover(1L, aisyah.id(), FIRST_AID, TODAY.plusDays(6)));

		assertThatThrownBy(() -> service.assign(elder.id(), aisyah.id()))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("covers them only until 2 Oct 2026, and the care plan needs it from 3 Oct 2026");
	}

	@Test
	void namesACaregiverWhoseCertificatesCoverThePlan() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		plans.add(plan(9L, elder.id(), TODAY.minusDays(30), null));
		requiredTypes.put(9L, Set.of(FIRST_AID));
		// An expired certificate next to its renewal: the renewal's later day counts.
		covers.add(new Cover(1L, aisyah.id(), FIRST_AID, TODAY.minusDays(1)));
		covers.add(new Cover(2L, aisyah.id(), FIRST_AID, TODAY.plusYears(2)));

		service.assign(elder.id(), aisyah.id());

		assertThat(assignments.findByElderId(elder.id())).get()
				.extracting(PrimaryCaregiverAssignment::caregiverId).isEqualTo(aisyah.id());
	}

	@Test
	void ignoresWhatAPlanThatHasEndedRequired() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		plans.add(plan(8L, elder.id(), TODAY.minusDays(60), TODAY));
		requiredTypes.put(8L, Set.of(FIRST_AID));

		service.assign(elder.id(), aisyah.id());

		assertThat(assignments.findByElderId(elder.id())).isPresent();
	}

	@Test
	void rostersVisitsToTheNamedCaregiverWhileTheyCanTakeThem() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.BUSY);
		service.assign(elder.id(), aisyah.id());

		assertThat(service.findRosterableCaregiverId(elder.id())).contains(aisyah.id());
	}

	@Test
	void namesAPrimaryCaregiverWhoCantTakeVisitsSoTheirsCanBeReleased() {
		Elder unassigned = saveElder();
		Elder active = saveElder();
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.BUSY);
		Caregiver inactive = caregivers.save("Left Last Month", Caregiver.Status.INACTIVE);
		service.assign(active.id(), aisyah.id());
		assignments.save(new PrimaryCaregiverAssignment(elder.id(), inactive.id(), NOW));

		assertThat(service.findUnrosterableCaregiverId(unassigned.id())).isEmpty();
		assertThat(service.findUnrosterableCaregiverId(active.id())).isEmpty();
		assertThat(service.findUnrosterableCaregiverId(elder.id())).contains(inactive.id());
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

	@Test
	void refusesACaregiverWhoseOtherElderHasAVisitAtTheSameTime() {
		Elder held = saveElder();
		Elder wanted = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		plans.add(weekly(8L, held.id(), "Bathing assistance", DayOfWeek.MONDAY, LocalTime.of(8, 0)));
		plans.add(weekly(9L, wanted.id(), "Meal preparation", DayOfWeek.MONDAY, LocalTime.of(8, 15)));
		service.assign(held.id(), aisyah.id());
		events.clear();

		assertThatThrownBy(() -> service.assign(wanted.id(), aisyah.id()))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessage("Aisyah N. is already primary caregiver of Chan Bee Choo, whose Bathing assistance on "
						+ "Mon 08:00–08:30 clashes with Meal preparation on Mon 08:15–08:45")
				.extracting(e -> ((BusinessRuleViolation) e).code()).isEqualTo("CAREGIVER_TIME_CLASH");
		assertThat(assignments.findByElderId(wanted.id())).isEmpty();
		assertThat(events).isEmpty();
	}

	@Test
	void namesACaregiverWhoseOtherEldersVisitsAreAtOtherTimes() {
		Elder held = saveElder();
		Elder wanted = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		plans.add(weekly(8L, held.id(), "Bathing assistance", DayOfWeek.MONDAY, LocalTime.of(8, 0)));
		plans.add(weekly(9L, wanted.id(), "Meal preparation", DayOfWeek.MONDAY, LocalTime.of(9, 0)));
		service.assign(held.id(), aisyah.id());

		service.assign(wanted.id(), aisyah.id());

		assertThat(assignments.findByCaregiverId(aisyah.id())).hasSize(2);
	}

	@Test
	void refusesACaregiverLeftTooLittleTimeToTravelBetweenTwoElders() {
		Elder held = saveElder();
		Elder wanted = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		plans.add(weekly(8L, held.id(), "Bathing assistance", DayOfWeek.MONDAY, LocalTime.of(8, 0)));
		plans.add(weekly(9L, wanted.id(), "Meal preparation", DayOfWeek.MONDAY, LocalTime.of(8, 30)));
		service.assign(held.id(), aisyah.id());

		assertThatThrownBy(() -> service.assign(wanted.id(), aisyah.id()))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessage("Aisyah N. is already primary caregiver of Chan Bee Choo, whose Bathing assistance on "
						+ "Mon 08:00–08:30 is less than 30 minutes from Meal preparation on Mon 08:30–09:00, not "
						+ "enough time to travel between them")
				.extracting(e -> ((BusinessRuleViolation) e).code()).isEqualTo("CAREGIVER_TIME_CLASH");
	}

	@Test
	void namingTheSameCaregiverAgainDoesNotClashWithTheirOwnElder() {
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		plans.add(weekly(9L, elder.id(), "Meal preparation", DayOfWeek.MONDAY, LocalTime.of(8, 0)));
		service.assign(elder.id(), aisyah.id());

		service.assign(elder.id(), aisyah.id());

		assertThat(assignments.findByElderId(elder.id())).isPresent();
	}

	@Test
	void aNewPlanVersionIsCheckedAgainstThePrimaryCaregiversOtherElders() {
		Elder held = saveElder();
		Elder elder = saveElder();
		Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		plans.add(weekly(8L, held.id(), "Bathing assistance", DayOfWeek.MONDAY, LocalTime.of(8, 0)));
		plans.add(weekly(9L, elder.id(), "Meal preparation", DayOfWeek.MONDAY, LocalTime.of(10, 0)));
		service.assign(held.id(), aisyah.id());
		service.assign(elder.id(), aisyah.id());

		// The new version moves meals onto the other elder's bathing; its own current plan doesn't count.
		assertThat(service.clashFor(elder.id(), TODAY.plusDays(7), List.of(task("Meal preparation",
				DayOfWeek.MONDAY, LocalTime.of(8, 0)))))
				.contains("Aisyah N. is already primary caregiver of Chan Bee Choo, whose Bathing assistance on "
						+ "Mon 08:00–08:30 clashes with Meal preparation on Mon 08:00–08:30");
		assertThat(service.clashFor(elder.id(), TODAY.plusDays(7), List.of(task("Meal preparation",
				DayOfWeek.MONDAY, LocalTime.of(10, 0))))).isEmpty();
	}

	@Test
	void aNewPlanVersionHasNothingToClashWithoutAPrimaryCaregiverWhoCanTakeVisits() {
		Elder held = saveElder();
		Elder elder = saveElder();
		Caregiver leaving = caregivers.save("Leaving Soon", Caregiver.Status.AVAILABLE);
		plans.add(weekly(8L, held.id(), "Bathing assistance", DayOfWeek.MONDAY, LocalTime.of(8, 0)));
		List<CarePlanSchedules.Task> clashing = List.of(task("Meal preparation", DayOfWeek.MONDAY, LocalTime.of(8, 0)));

		assertThat(service.clashFor(elder.id(), TODAY, clashing)).isEmpty();

		service.assign(held.id(), leaving.id());
		assignments.save(new PrimaryCaregiverAssignment(elder.id(), leaving.id(), NOW));
		assertThat(service.clashFor(elder.id(), TODAY, clashing)).isPresent();

		caregivers.save(new Caregiver(leaving.id(), leaving.userId(), leaving.fullName(), leaving.phone(),
				leaving.sector(), leaving.dialects(), Caregiver.Status.INACTIVE, null, null));
		assertThat(service.clashFor(elder.id(), TODAY, clashing)).isEmpty();
	}

	private static CarePlanSchedules.Task task(String name, DayOfWeek day, LocalTime start) {
		return new CarePlanSchedules.Task(null, "Care", null, name, List.of(new CarePlanSchedules.Slot(day, start, 30)));
	}

	@Test
	void listsEveryCaregiverForAnElderWithTheFirstRuleThatStopsThem() {
		Elder held = saveElder();
		Elder elder = saveElder();
		Caregiver free = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
		Caregiver busyElsewhere = caregivers.save("Devi Raman", Caregiver.Status.AVAILABLE);
		Caregiver uncertified = caregivers.save("Ken Lim", Caregiver.Status.AVAILABLE);
		Caregiver inactive = caregivers.save("Left Last Month", Caregiver.Status.INACTIVE);
		plans.add(weekly(8L, held.id(), "Bathing assistance", DayOfWeek.MONDAY, LocalTime.of(8, 0)));
		plans.add(weekly(9L, elder.id(), "Meal preparation", DayOfWeek.MONDAY, LocalTime.of(8, 0)));
		requiredTypes.put(9L, Set.of(FIRST_AID));
		for (Caregiver certified : List.of(free, busyElsewhere, inactive)) {
			covers.add(new Cover(certified.id(), certified.id(), FIRST_AID, TODAY.plusYears(1)));
		}
		covers.add(new Cover(99L, busyElsewhere.id(), FIRST_AID, TODAY.plusYears(1)));
		service.assign(held.id(), busyElsewhere.id());

		assertThat(service.eligibilityFor(elder.id()))
				.extracting(e -> e.caregiver().fullName(), CaregiverEligibility::eligible, CaregiverEligibility::refusalCode)
				.containsExactlyInAnyOrder(
						tuple("Aisyah N.", true, null),
						tuple("Devi Raman", false, "CAREGIVER_TIME_CLASH"),
						tuple("Ken Lim", false, "CAREGIVER_MISSING_CREDENTIAL"),
						tuple("Left Last Month", false, "CAREGIVER_NOT_ASSIGNABLE"));
		assertThat(service.eligibilityFor(elder.id())).filteredOn(e -> e.caregiver().id().equals(uncertified.id()))
				.singleElement().extracting(CaregiverEligibility::refusal).asString()
				.startsWith("Ken Lim has no First Aid certificate");
		assertThatThrownBy(() -> service.eligibilityFor(999L)).isInstanceOf(ResourceNotFound.class);
	}

	/** A plan in force from a month ago with one 30-minute visit a week. */
	private static PlanSchedule weekly(Long id, Long elderId, String task, DayOfWeek day, LocalTime start) {
		return new PlanSchedule(id, elderId, 1, TODAY.minusDays(30), null, List.of(new CarePlanSchedules.Task(
				id * 10, "Care", null, task, List.of(new CarePlanSchedules.Slot(day, start, 30)))));
	}

	private static PlanSchedule plan(Long id, Long elderId, LocalDate from, LocalDate until) {
		return new PlanSchedule(id, elderId, 1, from, until, List.of());
	}

	private Elder saveElder() {
		return elders.save(new Elder(null, null, "Chan Bee Choo", null, null, null, null, null, "S31", null,
				null, null, Elder.ContinuityPreference.PREFERRED, null, null, null));
	}
}
