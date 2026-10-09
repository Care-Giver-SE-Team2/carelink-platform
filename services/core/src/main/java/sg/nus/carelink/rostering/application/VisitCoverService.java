package sg.nus.carelink.rostering.application;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateCheckRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringConstraintRepository;
import sg.nus.carelink.rostering.domain.service.ReplacementFinder;
import sg.nus.carelink.rostering.domain.service.ScoringObjective;
import sg.nus.carelink.rostering.domain.service.Shortlist;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.application.VisitReassignment;

/**
 * Runs the replacement search for one visit nobody holds and, at the manager's word, puts the
 * chosen caregiver on it. Unlike an absence's search, nothing is recorded as a rostering run:
 * there is no absence to account to, and the visit's assignment history already says who put
 * whom on it. The default objective, continuity, ranks the suggestions.
 */
@Service
@Transactional
class VisitCoverService implements VisitCover {

	static final String REASON = "A manager covered the visit, which had no caregiver";

	private final VisitReassignment visits;
	private final RosterSnapshotLoader snapshots;
	private final ReplacementSearchRecord search;

	VisitCoverService(VisitReassignment visits, RosterSnapshotLoader snapshots,
			RosteringConstraintRepository constraints, RosteringCandidateRepository candidates,
			RosteringCandidateCheckRepository checks) {
		this.visits = visits;
		this.snapshots = snapshots;
		this.search = new ReplacementSearchRecord(constraints, candidates, checks);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Option> options(Long visitId) {
		return shortlist(openSlot(visitId)).all().stream()
				.map(verdict -> new Option(verdict.caregiverId(), verdict.name(), verdict.rank(), verdict.reason()))
				.toList();
	}

	@Override
	public void cover(Long visitId, Long caregiverId, Long byUserId) {
		Objects.requireNonNull(caregiverId, "caregiverId");
		Shortlist shortlist = shortlist(openSlot(visitId));
		if (!shortlist.isFeasible(caregiverId)) {
			String why = shortlist.excluded().stream()
					.filter(verdict -> verdict.caregiverId().equals(caregiverId))
					.map(Shortlist.Verdict::reason)
					.findFirst()
					.orElse("they are not a caregiver who can be rostered");
			throw new BusinessRuleViolation("CAREGIVER_CANNOT_COVER",
					"This caregiver cannot take the visit: " + why);
		}
		visits.reassign(visitId, caregiverId, new VisitReassignment.Change(null, byUserId, null, REASON));
	}

	private Shortlist shortlist(VacatedSlot slot) {
		ReplacementFinder finder = new ReplacementFinder(search.ruleSet().rules(), ScoringObjective.of(null));
		return finder.shortlist(slot, snapshots.load(List.of(slot)));
	}

	/** The visit as the search sees it, provided nobody holds it and nobody has started it. */
	private VacatedSlot openSlot(Long visitId) {
		VisitReassignment.VisitSlot visit = visits.find(visitId)
				.orElseThrow(() -> new ResourceNotFound("Visit", visitId));
		if (visit.caregiverId() != null) {
			throw new BusinessRuleViolation("VISIT_ALREADY_COVERED",
					"Visit %d already has a caregiver".formatted(visitId));
		}
		if (!"SCHEDULED".equals(visit.status())) {
			throw new BusinessRuleViolation("VISIT_NOT_OPEN",
					"Visit %d is %s and can no longer be covered".formatted(visitId, visit.status()));
		}
		return new VacatedSlot(visit.visitId(), visit.elderId(), visit.carePlanId(), visit.serviceType(),
				visit.start(), visit.end(), null);
	}
}
