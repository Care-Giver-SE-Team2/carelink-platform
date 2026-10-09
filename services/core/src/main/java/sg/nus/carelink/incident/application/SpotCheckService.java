package sg.nus.carelink.incident.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.incident.domain.repository.SpotCheckAlert;
import sg.nus.carelink.incident.domain.repository.SpotCheckLookups;
import sg.nus.carelink.incident.domain.repository.SpotCheckRepository;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * UC-MG08 conduct a home service spot check, with the family's consent (the former UC-FM07)
 * and the checked caregiver's right to see and answer the conclusion.
 *
 * <p>Each public method is one step: load, ask {@link SpotCheck} whether the step is allowed,
 * save, tell people. A caregiver who did not turn up becomes an incident through
 * {@link IncidentService}, the same route every missed visit takes. Conclusions are also offered
 * to rostering through {@link SpotCheckHistory}.
 */
@Service
@Transactional
public class SpotCheckService implements SpotCheckHistory {

	/** How far ahead a manager may pick a visit to check: the roster window. */
	static final Duration HORIZON = Duration.ofDays(14);

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

	private final SpotCheckRepository checks;
	private final SpotCheckLookups lookups;
	private final SpotCheckAlert alert;
	private final IncidentService incidents;
	private final FamilyAccessQuery familyAccess;
	private final UserDirectory users;
	private final CaregiverWorkDirectory caregivers;
	private final RosteringProfiles profiles;
	private final Clock clock;

	@SuppressWarnings("java:S107") // one collaborator per module the use case touches
	public SpotCheckService(SpotCheckRepository checks, SpotCheckLookups lookups, SpotCheckAlert alert,
			IncidentService incidents, FamilyAccessQuery familyAccess, UserDirectory users,
			CaregiverWorkDirectory caregivers, RosteringProfiles profiles, Clock clock) {
		this.checks = checks;
		this.lookups = lookups;
		this.alert = alert;
		this.incidents = incidents;
		this.familyAccess = familyAccess;
		this.users = users;
		this.caregivers = caregivers;
		this.profiles = profiles;
		this.clock = clock;
	}

	// ------------------------------------------------------------------ the manager ---

	/**
	 * Steps 1 and 2: a manager asks to watch a visit that has not started, and the family is
	 * asked. One open request per visit.
	 */
	public SpotCheck request(Long visitId, String purpose, Long managerUserId) {
		SpotCheckLookups.VisitFacts visit = openVisit(visitId);
		boolean alreadyAsked = checks.findByElderIds(Set.of(visit.elderId())).stream()
				.anyMatch(check -> Objects.equals(check.visitId(), visitId) && check.isOpen());
		if (alreadyAsked) {
			throw new BusinessRuleViolation("SPOT_CHECK_ALREADY_REQUESTED",
					"This visit already has a spot check waiting or scheduled");
		}
		SpotCheck saved = checks.save(SpotCheck.requested(visit.elderId(), visitId, visit.caregiverId(), visit.start(),
				purpose, managerUserId, now()));
		alert.approvalRequested(saved, names(saved));
		return saved;
	}

	/** Steps 4 and 5: the conclusion recorded on site; the family and the caregiver can read it. */
	public SpotCheck conclude(Long checkId, SpotCheck.Result result, String notes) {
		SpotCheck concluded = checks.save(require(checkId).concluded(result, notes, now()));
		alert.concluded(concluded, names(concluded));
		return concluded;
	}

	/**
	 * Exception 4a: the caregiver did not turn up. The check closes and the missed visit becomes
	 * an incident in the exception queue. One transaction: if the check may not be closed, no
	 * incident is left behind either.
	 */
	public SpotCheck reportNoShow(Long checkId, String notes, String actor) {
		SpotCheck check = require(checkId);
		check.ensureScheduled();
		Long incidentId = incidents.raiseForMissedSpotCheck(check.elderId(), check.visitId(),
				"The caregiver did not turn up for the spot-checked visit at %s".formatted(check.proposedTime().format(WHEN)),
				actor).id();
		return checks.save(check.caregiverDidNotTurnUp(incidentId, notes, now()));
	}

	/**
	 * Exception 4b: the elder was out, so the check moves to another of their visits and the
	 * family is asked again. Nothing is held against the caregiver.
	 */
	public SpotCheck moveTo(Long checkId, Long newVisitId) {
		SpotCheck check = require(checkId);
		SpotCheckLookups.VisitFacts visit = openVisit(newVisitId);
		if (!Objects.equals(visit.elderId(), check.elderId())) {
			throw new BusinessRuleViolation("SPOT_CHECK_OTHER_ELDER", "A spot check can only move to the same elder's visit");
		}
		SpotCheck moved = checks.save(check.movedTo(newVisitId, visit.caregiverId(), visit.start(), now()));
		alert.approvalRequested(moved, names(moved));
		return moved;
	}

	/** The manager calls the request off, with the reason the use case asks to keep. */
	public SpotCheck withdraw(Long checkId, String reason) {
		SpotCheck withdrawn = checks.save(require(checkId).withdrawn(reason, now()));
		alert.withdrawn(withdrawn, names(withdrawn));
		return withdrawn;
	}

	/** Every spot check, latest first, narrowed by stage, caregiver or elder when asked. */
	@Transactional(readOnly = true)
	public List<SpotCheckView> list(SpotCheck.Stage stage, Long caregiverId, Long elderId) {
		Names names = new Names();
		return checks.findAll().stream()
				.filter(check -> stage == null || check.stage() == stage)
				.filter(check -> caregiverId == null || Objects.equals(check.caregiverId(), caregiverId))
				.filter(check -> elderId == null || Objects.equals(check.elderId(), elderId))
				.map(check -> view(check, names))
				.toList();
	}

	/** One caregiver's concluded checks: what their record shows, and what rostering weighs. */
	@Transactional(readOnly = true)
	public List<SpotCheckView> conclusionsFor(Long caregiverId) {
		Names names = new Names();
		return checks.findByCaregiverId(caregiverId).stream()
				.filter(check -> check.stage() == SpotCheck.Stage.COMPLETED)
				.map(check -> view(check, names))
				.toList();
	}

	/** The elder's visits a manager may choose to check: somebody on them, not started, in the next two weeks. */
	@Transactional(readOnly = true)
	public List<VisitChoice> visitsToCheck(Long elderId) {
		LocalDateTime now = now();
		Names names = new Names();
		return lookups.upcomingVisits(elderId, now, now.plus(HORIZON)).stream()
				.map(visit -> new VisitChoice(visit.visitId(), visit.start(), visit.serviceType(), visit.caregiverId(),
						names.caregiver(visit.caregiverId())))
				.toList();
	}

	// ------------------------------------------------------------------ the family ---

	/**
	 * Step 3 or alternative 3a: a family member bound to the elder agrees, or declines and says
	 * why. Nobody comes to watch without this.
	 */
	public SpotCheck decide(Long checkId, String familyUsername, boolean approve, String reason) {
		SpotCheck check = require(checkId);
		familyAccess.requireReadableElder(familyUsername, check.elderId());
		Long familyMemberId = users.findByUsername(familyUsername).map(AppUser::id)
				.flatMap(lookups::familyMemberIdOf)
				.orElse(null);
		SpotCheck decided = checks.save(approve
				? check.approvedBy(familyMemberId, now())
				: check.declinedBy(familyMemberId, reason, now()));
		alert.familyAnswered(decided, names(decided));
		return decided;
	}

	/** Spot checks of the elders this family member may read, those waiting for them first. */
	@Transactional(readOnly = true)
	public List<SpotCheckView> forFamily(String familyUsername) {
		Set<Long> elderIds = familyAccess.readableElderIds(familyUsername);
		Names names = new Names();
		return checks.findByElderIds(elderIds).stream()
				.sorted(Comparator.comparing((SpotCheck c) -> c.stage() != SpotCheck.Stage.AWAITING_FAMILY))
				.map(check -> view(check, names))
				.toList();
	}

	// ------------------------------------------------------------------ the caregiver ---

	/** The caregiver's own concluded checks: "抽查结论对护理员可见". Requests before the day are not shown. */
	@Transactional(readOnly = true)
	public List<SpotCheckView> forCaregiver(String caregiverUsername) {
		return conclusionsFor(caregivers.require(caregiverUsername).id());
	}

	/** The checked caregiver answers the conclusion. */
	public SpotCheck respond(Long checkId, String caregiverUsername, String response) {
		Long caregiverId = caregivers.require(caregiverUsername).id();
		return checks.save(require(checkId).respondedBy(caregiverId, response));
	}

	// ------------------------------------------------------------------ reminders ---

	/**
	 * The family's side, exception 1a: a request they have not answered stays open - never taken
	 * as a yes - and they are asked again. These are the requests whose visit is still ahead and
	 * whose family was last asked at least {@code after} ago.
	 */
	@Transactional(readOnly = true)
	public List<Long> dueForReminder(Duration after) {
		LocalDateTime now = now();
		return checks.findAwaitingConsent().stream()
				.filter(check -> check.proposedTime().isAfter(now))
				.filter(check -> {
					LocalDateTime lastAsked = lookups.lastAskedAt(check.id())
							.orElse(Objects.requireNonNullElse(check.createdAt(), now));
					return !lastAsked.plus(after).isAfter(now);
				})
				.map(SpotCheck::id)
				.toList();
	}

	/**
	 * Asks the family about one request again, if it is still theirs to answer. Its own
	 * transaction, so one request that cannot be reminded holds back none of the others.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean remind(Long checkId) {
		SpotCheck check = require(checkId);
		if (check.stage() != SpotCheck.Stage.AWAITING_FAMILY || !check.proposedTime().isAfter(now())) {
			return false;
		}
		alert.reminded(check, names(check));
		return true;
	}

	// ------------------------------------------------------------------ rostering ---

	@Override
	@Transactional(readOnly = true)
	public Map<Long, Conclusions> recentConclusions(LocalDateTime since) {
		Map<Long, int[]> counts = new HashMap<>();
		for (SpotCheck check : checks.findConcludedSince(since)) {
			int[] tally = counts.computeIfAbsent(check.caregiverId(), id -> new int[2]);
			tally[check.result() == SpotCheck.Result.MEETS_STANDARD ? 0 : 1]++;
		}
		return counts.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> new Conclusions(e.getValue()[0], e.getValue()[1])));
	}

	// ------------------------------------------------------------------ helpers ---

	@Transactional(readOnly = true)
	public SpotCheckView view(Long checkId) {
		return view(require(checkId), new Names());
	}

	private SpotCheckLookups.VisitFacts openVisit(Long visitId) {
		SpotCheckLookups.VisitFacts visit = lookups.visit(visitId)
				.orElseThrow(() -> new ResourceNotFound("Visit", visitId));
		if (!visit.isScheduled()) {
			throw new BusinessRuleViolation("SPOT_CHECK_VISIT_NOT_OPEN",
					"Visit %d is %s; only a visit that has not started can be checked".formatted(visitId, visit.status()));
		}
		return visit;
	}

	private SpotCheck require(Long checkId) {
		return checks.findById(checkId).orElseThrow(() -> new ResourceNotFound("SpotCheck", checkId));
	}

	private SpotCheckAlert.Names names(SpotCheck check) {
		Names names = new Names();
		return new SpotCheckAlert.Names(names.elder(check.elderId()), names.caregiver(check.caregiverId()));
	}

	private SpotCheckView view(SpotCheck check, Names names) {
		return new SpotCheckView(check.id(), check.elderId(), names.elder(check.elderId()), check.caregiverId(),
				names.caregiver(check.caregiverId()), check.visitId(), check.proposedTime(), check.reason(), check.stage(),
				check.decidedAt(), check.result(), check.finding(), check.checkedAt(), check.closingReason(),
				check.incidentId(), check.caregiverResponse());
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	/** Looks names up once per call, however many checks mention the same person. */
	private final class Names {

		private Map<Long, String> caregiverNames;
		private final Map<Long, String> elderNames = new HashMap<>();

		String caregiver(Long caregiverId) {
			if (caregiverId == null) {
				return null;
			}
			if (caregiverNames == null) {
				caregiverNames = profiles.candidates().stream().collect(Collectors.toMap(
						RosteringProfiles.Candidate::caregiverId, RosteringProfiles.Candidate::fullName, (a, b) -> a));
			}
			return caregiverNames.getOrDefault(caregiverId, "Caregiver #" + caregiverId);
		}

		String elder(Long elderId) {
			return elderNames.computeIfAbsent(elderId, id -> profiles.elder(id)
					.map(RosteringProfiles.ElderFacts::fullName)
					.orElse("Elder #" + id));
		}
	}

	/** A spot check as the screens show it. */
	public record SpotCheckView(Long id, Long elderId, String elderName, Long caregiverId, String caregiverName,
			Long visitId, LocalDateTime visitTime, String purpose, SpotCheck.Stage stage, LocalDateTime decidedAt,
			SpotCheck.Result result, String notes, LocalDateTime checkedAt, String closingReason, Long incidentId,
			String caregiverResponse) {
	}

	/** A visit a manager may choose to check. */
	public record VisitChoice(Long visitId, LocalDateTime start, String serviceType, Long caregiverId,
			String caregiverName) {
	}
}
