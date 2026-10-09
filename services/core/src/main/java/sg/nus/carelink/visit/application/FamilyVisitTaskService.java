package sg.nus.carelink.visit.application;

import java.util.List;

import org.springframework.stereotype.Service;

import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyReadAudit;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.VisitTask;
import sg.nus.carelink.visit.domain.repository.VisitRepository;
import sg.nus.carelink.visit.domain.repository.VisitTaskRepository;

/**
 * Reads stored task progress under the family's current binding and audits the outcome.
 *
 * @author Wang Zhili
 */
@Service
public class FamilyVisitTaskService {

	private final VisitRepository visits;
	private final VisitTaskRepository tasks;
	private final FamilyAccessQuery access;
	private final FamilyReadAudit audit;

	public FamilyVisitTaskService(VisitRepository visits, VisitTaskRepository tasks,
			FamilyAccessQuery access, FamilyReadAudit audit) {
		this.visits = visits;
		this.tasks = tasks;
		this.access = access;
		this.audit = audit;
	}

	/** Checks the parent visit even when it has no tasks; stored execution facts are unchanged. */
	public List<VisitTask> findTasks(String username, Long visitId) {
		return audit.read(username, FamilyReadAudit.Resource.VISIT_TASKS, visitId, "", () -> {
			var visit = visits.findById(visitId).orElseThrow(() -> new ResourceNotFound("Visit", visitId));
			access.requireReadableElder(username, visit.elderId());
			return tasks.findByVisitId(visitId);
		});
	}
}
