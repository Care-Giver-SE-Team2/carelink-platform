package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.careplan.application.CarePlanRequirements;
import sg.nus.carelink.careplan.application.PrimaryCaregiverClashCheck;
import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.careplan.application.CarePlanSchedules.PlanSchedule;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.profile.domain.repository.PrimaryCaregiverAssignmentRepository;
import sg.nus.carelink.profile.domain.service.PrimaryCaregiverCertification;
import sg.nus.carelink.profile.domain.service.PrimaryCaregiverCertification.Gap;
import sg.nus.carelink.profile.domain.service.PrimaryCaregiverCertification.Need;
import sg.nus.carelink.profile.domain.service.PrimaryCaregiverClash;
import sg.nus.carelink.profile.domain.service.PrimaryCaregiverClash.Clash;
import sg.nus.carelink.profile.domain.service.PrimaryCaregiverClash.Plan;
import sg.nus.carelink.profile.domain.service.PrimaryCaregiverClash.Slot;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * UC-MG02: the manager names, changes or removes an elder's primary caregiver from the Elders
 * index. Whether a caregiver may be named is Caregiver.isAssignable(), PrimaryCaregiverCertification
 * and PrimaryCaregiverClash; this class loads what they need, checks existence and saves. It also
 * answers careplan's PrimaryCaregiverClashCheck, so a plan published later is held to the same
 * clash rule as the caregiver was when they were named.
 */
@Service
@Transactional
public class PrimaryCaregiverService implements PrimaryCaregiverLookup, PrimaryCaregiverClashCheck {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private final ElderRepository elders;
	private final CaregiverRepository caregivers;
	private final PrimaryCaregiverAssignmentRepository assignments;
	private final CarePlanSchedules plans;
	private final CarePlanRequirements requirements;
	private final CredentialRegister register;
	private final CredentialTypeRepository credentialTypes;
	private final Clock clock;
	private final ApplicationEventPublisher events;
	private final int travelBufferMinutes;

	public PrimaryCaregiverService(ElderRepository elders, CaregiverRepository caregivers,
			PrimaryCaregiverAssignmentRepository assignments, CarePlanSchedules plans,
			CarePlanRequirements requirements, CredentialRegister register, CredentialTypeRepository credentialTypes,
			Clock clock, ApplicationEventPublisher events,
			@Value("${carelink.roster.travel-buffer-minutes:30}") int travelBufferMinutes) {
		this.elders = elders;
		this.caregivers = caregivers;
		this.assignments = assignments;
		this.plans = plans;
		this.requirements = requirements;
		this.register = register;
		this.credentialTypes = credentialTypes;
		this.clock = clock;
		this.events = events;
		this.travelBufferMinutes = travelBufferMinutes;
	}

	/** Every caregiver the picker lists, ineligible ones included so the manager sees why. */
	@Transactional(readOnly = true)
	public List<Caregiver> listCaregivers() {
		return caregivers.findAll();
	}

	/**
	 * Names the caregiver as the elder's primary caregiver, replacing any existing one.
	 *
	 * @throws ResourceNotFound if the elder or caregiver does not exist
	 * @throws BusinessRuleViolation CAREGIVER_NOT_ASSIGNABLE if the caregiver is onboarding or inactive,
	 *         CAREGIVER_MISSING_CREDENTIAL if they lack a certificate the elder's care plan requires, or
	 *         CAREGIVER_TIME_CLASH if the plan has a visit at the same time as one in the plan of
	 *         another elder they are primary caregiver of
	 */
	public PrimaryCaregiverSummary assign(Long elderId, Long caregiverId) {
		elders.findById(elderId).orElseThrow(() -> new ResourceNotFound("Elder", elderId));
		Caregiver caregiver = caregivers.findById(caregiverId)
				.orElseThrow(() -> new ResourceNotFound("Caregiver", caregiverId));
		refusalFor(new ElderPlans(elderId), caregiver).ifPresent(refusal -> {
			throw new BusinessRuleViolation(refusal.code(), refusal.message());
		});
		Long previous = assignments.findByElderId(elderId)
				.map(PrimaryCaregiverAssignment::caregiverId)
				.filter(id -> !id.equals(caregiverId))
				.orElse(null);
		PrimaryCaregiverAssignment saved = assignments.save(
				new PrimaryCaregiverAssignment(elderId, caregiverId, LocalDateTime.now(clock)));
		events.publishEvent(new PrimaryCaregiverChanged(elderId, previous));
		return new PrimaryCaregiverSummary(caregiver.id(), caregiver.fullName(), saved.assignedAt());
	}

	/**
	 * Removes the elder's primary caregiver; a no-op if none is assigned. The visits nobody has
	 * started come off them and are left to be covered.
	 */
	public void unassign(Long elderId) {
		elders.findById(elderId).orElseThrow(() -> new ResourceNotFound("Elder", elderId));
		Optional<PrimaryCaregiverAssignment> removed = assignments.findByElderId(elderId);
		if (removed.isEmpty()) {
			return;
		}
		assignments.deleteByElderId(elderId);
		events.publishEvent(new PrimaryCaregiverChanged(elderId, removed.get().caregiverId()));
	}

	/**
	 * Every caregiver the picker lists for this elder, ineligible ones included, each with why they
	 * can't be named its primary caregiver: the checks assign makes, run before the manager picks.
	 *
	 * @throws ResourceNotFound if the elder does not exist
	 */
	@Transactional(readOnly = true)
	public List<CaregiverEligibility> eligibilityFor(Long elderId) {
		elders.findById(elderId).orElseThrow(() -> new ResourceNotFound("Elder", elderId));
		ElderPlans elder = new ElderPlans(elderId);
		return caregivers.findAll().stream()
				.map(caregiver -> refusalFor(elder, caregiver)
						.map(refusal -> new CaregiverEligibility(caregiver, refusal.code(), refusal.message()))
						.orElseGet(() -> new CaregiverEligibility(caregiver, null, null)))
				.toList();
	}

	/**
	 * Why the caregiver can't be named the elder's primary caregiver, first rule first: they are
	 * onboarding or inactive; they lack a certificate the elder's plan requires on the first day
	 * they would work it; or its visits fall at the same time as, or too close to travel to, the
	 * visits of another elder they are primary caregiver of.
	 */
	private Optional<Refusal> refusalFor(ElderPlans elder, Caregiver caregiver) {
		if (!caregiver.isAssignable()) {
			return Optional.of(new Refusal("CAREGIVER_NOT_ASSIGNABLE",
					caregiver.fullName() + " cannot be assigned while " + caregiver.status()));
		}
		Optional<Gap> gap = PrimaryCaregiverCertification.firstGap(elder.needs(), elder.coveredUntil(caregiver.id()));
		if (gap.isPresent()) {
			return Optional.of(new Refusal("CAREGIVER_MISSING_CREDENTIAL", refusal(caregiver, gap.get())));
		}
		return clashWithOtherElders(elder.elderId, caregiver, elder.plans(), elder.today)
				.map(reason -> new Refusal("CAREGIVER_TIME_CLASH", reason));
	}

	private record Refusal(String code, String message) {
	}

	/**
	 * What deciding about one elder needs, each part read on first use and then kept, so the
	 * picker can ask about every caregiver without reading the elder's plans or the certificate
	 * register again for each.
	 */
	private final class ElderPlans {

		private final Long elderId;
		private final LocalDate today = LocalDate.now(clock.withZone(SINGAPORE));
		private List<Plan> plans;
		private List<Need> needs;
		private Map<Long, Map<Long, LocalDate>> coveredUntil;

		ElderPlans(Long elderId) {
			this.elderId = elderId;
		}

		List<Plan> plans() {
			if (plans == null) {
				plans = plansOf(elderId);
			}
			return plans;
		}

		/**
		 * The certificates the elder's plans in force or yet to start require, each from the first
		 * day the caregiver would work it. Versions that have ended don't count.
		 */
		List<Need> needs() {
			if (needs == null) {
				List<PlanSchedule> current = PrimaryCaregiverService.this.plans.forElder(elderId).stream()
						.filter(plan -> plan.effectiveUntil() == null
								|| firstDay(plan, today).isBefore(plan.effectiveUntil()))
						.toList();
				Map<Long, Set<Long>> required = current.isEmpty() ? Map.of()
						: requirements.requiredCredentialTypes(current.stream().map(PlanSchedule::carePlanId).toList());
				needs = current.stream()
						.filter(plan -> required.containsKey(plan.carePlanId()))
						.map(plan -> new Need(firstDay(plan, today), required.get(plan.carePlanId())))
						.toList();
			}
			return needs;
		}

		/** The caregiver's credential types, each with the last day it covers them. */
		Map<Long, LocalDate> coveredUntil(Long caregiverId) {
			if (needs().isEmpty()) {
				return Map.of();
			}
			if (coveredUntil == null) {
				// A renewal waiting next to the certificate it replaces: the later day counts, as in rostering.
				coveredUntil = register.covers().stream().filter(cover -> cover.caregiverId() != null)
						.collect(Collectors.groupingBy(
						CredentialRegister.Cover::caregiverId,
						Collectors.toMap(CredentialRegister.Cover::credentialTypeId, CredentialRegister.Cover::coveredUntil,
								(a, b) -> a.isAfter(b) ? a : b)));
			}
			return coveredUntil.getOrDefault(caregiverId, Map.of());
		}
	}

	/**
	 * A new version of the elder's plan, from its start date, against the plans of the other
	 * elders their primary caregiver looks after. The versions it replaces don't count: they are
	 * the elder's own. A caregiver who can't take visits now (onboarding, inactive) gets none from
	 * the plan, so has nothing to clash.
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<String> clashFor(Long elderId, LocalDate startDate, List<CarePlanSchedules.Task> tasks) {
		Optional<Caregiver> primary = assignments.findByElderId(elderId)
				.flatMap(assignment -> caregivers.findById(assignment.caregiverId()))
				.filter(Caregiver::isAssignable);
		if (primary.isEmpty() || startDate == null) {
			return Optional.empty();
		}
		Plan wanted = new Plan(elderId, startDate, null, slotsOf(tasks));
		return clashWithOtherElders(elderId, primary.get(), List.of(wanted), LocalDate.now(clock.withZone(SINGAPORE)));
	}

	/** Why the caregiver can't take these plans of the elder alongside their other elders', if they can't. */
	private Optional<String> clashWithOtherElders(Long elderId, Caregiver caregiver, List<Plan> wanted, LocalDate today) {
		List<Long> otherElders = assignments.findByCaregiverId(caregiver.id()).stream()
				.map(PrimaryCaregiverAssignment::elderId)
				.filter(id -> !id.equals(elderId))
				.toList();
		if (otherElders.isEmpty() || wanted.isEmpty()) {
			return Optional.empty();
		}
		List<Plan> held = otherElders.stream().flatMap(id -> plansOf(id).stream()).toList();
		return PrimaryCaregiverClash.firstClash(wanted, held, today, travelBufferMinutes)
				.map(clash -> clashRefusal(caregiver, clash));
	}

	private List<Plan> plansOf(Long elderId) {
		return plans.forElder(elderId).stream()
				.map(plan -> new Plan(plan.elderId(), plan.effectiveFrom(), plan.effectiveUntil(), slotsOf(plan.tasks())))
				.toList();
	}

	private static List<Slot> slotsOf(List<CarePlanSchedules.Task> tasks) {
		return tasks.stream()
				.flatMap(task -> task.slots().stream()
						.map(slot -> new Slot(task.name(), slot.day(), slot.startTime(), slot.minutes())))
				.toList();
	}

	private String clashRefusal(Caregiver caregiver, Clash clash) {
		String other = elders.findById(clash.heldElderId()).map(Elder::fullName)
				.orElse("elder #" + clash.heldElderId());
		if (clash.overlapping()) {
			return "%s is already primary caregiver of %s, whose %s clashes with %s".formatted(caregiver.fullName(),
					other, describe(clash.held()), describe(clash.wanted()));
		}
		return "%s is already primary caregiver of %s, whose %s is less than %d minutes from %s, not enough time "
				.formatted(caregiver.fullName(), other, describe(clash.held()), travelBufferMinutes,
						describe(clash.wanted()))
				+ "to travel between them";
	}

	private static String describe(Slot slot) {
		return "%s on %s %s–%s".formatted(slot.task(),
				slot.day().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
				slot.start().format(TIME), slot.end().format(TIME));
	}

	private static LocalDate firstDay(PlanSchedule plan, LocalDate today) {
		return plan.effectiveFrom().isAfter(today) ? plan.effectiveFrom() : today;
	}

	private String refusal(Caregiver caregiver, Gap gap) {
		String type = credentialTypes.findById(gap.credentialTypeId()).map(CredentialType::name)
				.orElse("credential type #" + gap.credentialTypeId());
		String needed = "the care plan needs it from " + gap.neededFrom().format(DAY);
		return gap.coveredUntil() == null
				? "%s has no %s certificate, and %s".formatted(caregiver.fullName(), type, needed)
				: "%s's %s certificate covers them only until %s, and %s".formatted(caregiver.fullName(), type,
						gap.coveredUntil().format(DAY), needed);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Long> findUnrosterableCaregiverId(Long elderId) {
		return assignments.findByElderId(elderId)
				.flatMap(assignment -> caregivers.findById(assignment.caregiverId()))
				.filter(caregiver -> !caregiver.isAssignable())
				.map(Caregiver::id);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Long> findRosterableCaregiverId(Long elderId) {
		return assignments.findByElderId(elderId)
				.flatMap(assignment -> caregivers.findById(assignment.caregiverId()))
				.filter(Caregiver::isAssignable)
				.map(Caregiver::id);
	}
}
