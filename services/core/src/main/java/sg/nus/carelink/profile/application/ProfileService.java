package sg.nus.carelink.profile.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.careplan.application.CarePlanLookup;
import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.profile.domain.repository.PrimaryCaregiverAssignmentRepository;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Application layer of the profile module
 * (elders, caregivers, family members, their bindings,
 * intake applications and credentials).
 *
 * <p>One public method per use case (UC-MG01, UC-MG02, UC-FM01, UC-EL04):
 * it loads what it needs through the domain ports, calls the domain model,
 * saves, and returns. Business rules stay in domain.model.
 */
@Service
@Transactional
public class ProfileService {

	private final ElderRepository elders;
	private final CarePlanLookup carePlans;
	private final PrimaryCaregiverAssignmentRepository primaryCaregivers;
	private final CaregiverRepository caregivers;

	public ProfileService(ElderRepository elders, CarePlanLookup carePlans,
			PrimaryCaregiverAssignmentRepository primaryCaregivers, CaregiverRepository caregivers) {
		this.elders = elders;
		this.carePlans = carePlans;
		this.primaryCaregivers = primaryCaregivers;
		this.caregivers = caregivers;
	}

	@Transactional(readOnly = true)
	public Optional<Elder> findElder(Long id) {
		return elders.findById(id);
	}

	/**
	 * UC-MG01 step one: every elder the manager can search, each with its latest plan status and
	 * next visit date.
	 */
	@Transactional(readOnly = true)
	public List<ElderSummary> listElders() {
		return summarize(elders.findAll(), LocalDate.now(ZoneId.systemDefault()));
	}

	/**
	 * Lists selected elders with plan status and the next planned visit date.
	 *
	 * @param elderIds Elder IDs authorized by the calling use case
	 * @param fromDate First date considered for the next planned visit
	 * @return Elder summaries in ascending ID order, or an empty list
	 * @author Wang Zhili
	 */
	@Transactional(readOnly = true)
	public List<ElderSummary> listEldersByIds(Set<Long> elderIds, LocalDate fromDate) {
		if (elderIds.isEmpty()) {
			return List.of();
		}
		return summarize(elders.findByIds(elderIds), fromDate);
	}

	private List<ElderSummary> summarize(List<Elder> selectedElders, LocalDate fromDate) {
		Map<Long, PrimaryCaregiverSummary> primaryByElder = primaryCaregiversOf(selectedElders);
		return selectedElders.stream()
				.map(elder -> toSummary(
						elder,
						carePlans.findLatestByElderId(elder.id()).orElse(null),
						carePlans.findNextVisitDate(elder.id(), fromDate).orElse(null),
						primaryByElder.get(elder.id())))
				.toList();
	}

	/** One query for the assignments and one for their caregivers, however many elders are listed. */
	private Map<Long, PrimaryCaregiverSummary> primaryCaregiversOf(List<Elder> selectedElders) {
		if (selectedElders.isEmpty()) {
			return Map.of();
		}
		Set<Long> elderIds = selectedElders.stream().map(Elder::id).collect(Collectors.toSet());
		List<PrimaryCaregiverAssignment> assignments = primaryCaregivers.findByElderIds(elderIds);
		if (assignments.isEmpty()) {
			return Map.of();
		}
		Set<Long> caregiverIds = assignments.stream()
				.map(PrimaryCaregiverAssignment::caregiverId).collect(Collectors.toSet());
		Map<Long, Caregiver> caregiverById = caregivers.findByIds(caregiverIds).stream()
				.collect(Collectors.toMap(Caregiver::id, Function.identity()));
		return assignments.stream()
				.filter(assignment -> caregiverById.containsKey(assignment.caregiverId()))
				.collect(Collectors.toMap(PrimaryCaregiverAssignment::elderId, assignment -> new PrimaryCaregiverSummary(
						assignment.caregiverId(),
						caregiverById.get(assignment.caregiverId()).fullName(),
						assignment.assignedAt())));
	}

	private static ElderSummary toSummary(Elder elder, CarePlan latestPlan, LocalDate nextVisitDate,
			PrimaryCaregiverSummary primaryCaregiver) {
		if (latestPlan == null || latestPlan.status() == CarePlan.Status.SUPERSEDED) {
			return new ElderSummary(elder, "none", null, null, primaryCaregiver);
		}
		if (latestPlan.status() == CarePlan.Status.STOPPED) {
			return new ElderSummary(elder, "stopped", latestPlan.version(), null, primaryCaregiver);
		}
		String status = latestPlan.status() == CarePlan.Status.PUBLISHED ? "published" : "draft";
		return new ElderSummary(elder, status, latestPlan.version(), nextVisitDate, primaryCaregiver);
	}

	    /**
     * Finds the elder profile linked to the authenticated app_user account.
     *
     * @param userId app_user.id
     * @return linked elder
     * @throws ResourceNotFound when the account has no elder profile
     */
    @Transactional(readOnly = true)
    public Elder requireElderByUserId(Long userId) {
        return elders.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFound("Elder for user", userId));
    }
}
