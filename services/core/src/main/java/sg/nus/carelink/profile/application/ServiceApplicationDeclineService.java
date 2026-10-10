package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.profile.domain.model.ServiceApplication;
import sg.nus.carelink.profile.domain.repository.ServiceApplicationRepository;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The manager declines a family's service application they won't plan, with a reason the family
 * is shown. One whose care is already in the plan has been answered and can't be declined.
 */
@Service
@Transactional
public class ServiceApplicationDeclineService {

	private final ServiceApplicationRepository applications;
	private final CareRequestProgress progress;
	private final UserDirectory users;
	private final Clock clock;

	ServiceApplicationDeclineService(ServiceApplicationRepository applications, CareRequestProgress progress,
			UserDirectory users, Clock clock) {
		this.applications = applications;
		this.progress = progress;
		this.users = users;
		this.clock = clock;
	}

	public ServiceApplication decline(Long applicationId, String reason, String managerUsername) {
		ServiceApplication application = applications.findById(applicationId)
				.orElseThrow(() -> new ResourceNotFound("Service application", applicationId));
		ServiceApplicationProgress.requireDeclinable(
				progress.of(application, progress.versions(application.elderId())));
		Long managerId = users.findByUsername(managerUsername)
				.orElseThrow(() -> new ResourceNotFound("Account", managerUsername))
				.id();
		return applications.save(application.decline(reason, managerId,
				LocalDateTime.now(clock.withZone(ZoneOffset.UTC))));
	}
}
