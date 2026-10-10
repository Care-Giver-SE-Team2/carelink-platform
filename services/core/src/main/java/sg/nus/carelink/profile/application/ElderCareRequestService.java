package sg.nus.carelink.profile.application;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.domain.repository.IntakeApplicationRepository;
import sg.nus.carelink.profile.domain.repository.ServiceApplicationRepository;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.PlanVersion;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.Progress;

/**
 * What the family has asked for on an elder's behalf, so the manager can build the care plan from
 * it: the approved intake application that created the elder, and any service applications since.
 */
@Service
@Transactional(readOnly = true)
public class ElderCareRequestService {

	private final IntakeApplicationRepository intakes;
	private final ServiceApplicationRepository serviceApplications;
	private final CareRequestProgress progress;

	ElderCareRequestService(IntakeApplicationRepository intakes,
			ServiceApplicationRepository serviceApplications, CareRequestProgress progress) {
		this.intakes = intakes;
		this.serviceApplications = serviceApplications;
		this.progress = progress;
	}

	/** Newest first; empty for an elder who registered themselves and has no applications. */
	public List<ElderCareRequest> listForElder(Long elderId) {
		List<PlanVersion> versions = progress.versions(elderId);
		Stream<ElderCareRequest> fromIntake = intakes.findApprovedByElderId(elderId).stream()
				.map(a -> {
					Progress p = progress.of(a, versions);
					return new ElderCareRequest(ElderCareRequest.Source.INTAKE, a.id(), a.careNeeds(),
							a.medicalNotes(), a.createdAt(), p.outcome(), p.needs(), null);
				});
		Stream<ElderCareRequest> fromServiceApplications = serviceApplications.findByElderId(elderId).stream()
				.map(a -> {
					Progress p = progress.of(a, versions);
					return new ElderCareRequest(ElderCareRequest.Source.SERVICE_APPLICATION, a.id(), a.careNeeds(),
							a.notes(), a.createdAt(), p.outcome(), p.needs(),
							a.decline() == null ? null : a.decline().reason());
				});
		return Stream.concat(fromIntake, fromServiceApplications)
				.sorted(Comparator.comparing(ElderCareRequest::submittedAt).reversed())
				.toList();
	}
}
