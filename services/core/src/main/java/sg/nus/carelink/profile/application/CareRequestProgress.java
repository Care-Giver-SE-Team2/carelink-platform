package sg.nus.carelink.profile.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.model.ServiceApplication;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.PlanVersion;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.Progress;

/**
 * Reads the elder's issued care plan versions through careplan's contract and works out how far
 * each of the family's applications has been answered (ServiceApplicationProgress).
 */
@Component
class CareRequestProgress {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	private final CarePlanSchedules schedules;

	CareRequestProgress(CarePlanSchedules schedules) {
		this.schedules = schedules;
	}

	List<PlanVersion> versions(Long elderId) {
		return schedules.forElder(elderId).stream()
				.map(plan -> new PlanVersion(plan.version(), plan.effectiveFrom(), plan.effectiveUntil(),
						plan.tasks().stream()
								.map(CarePlanSchedules.Task::activityCode)
								.filter(Objects::nonNull)
								.collect(Collectors.toSet())))
				.toList();
	}

	/** Service applications store created_at in UTC; the day they applied is the Singapore day. */
	Progress of(ServiceApplication application, List<PlanVersion> versions) {
		LocalDate submittedOn = application.createdAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(SINGAPORE).toLocalDate();
		return ServiceApplicationProgress.of(application.careNeeds(), submittedOn,
				application.status() == ServiceApplication.Status.DECLINED, versions);
	}

	/** An approved intake application can't be declined; it is planned or still waiting. */
	Progress of(IntakeApplication application, List<PlanVersion> versions) {
		LocalDateTime createdAt = application.createdAt();
		LocalDate submittedOn = createdAt == null ? LocalDate.MIN : createdAt.toLocalDate();
		return ServiceApplicationProgress.of(application.careNeeds(), submittedOn, false, versions);
	}
}
