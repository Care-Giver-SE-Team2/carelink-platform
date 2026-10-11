package sg.nus.carelink.visit.infrastructure.core;

import java.time.LocalDate;

import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.application.CaregiverWorkDirectory;

/**
 * {@link CaregiverWorkDirectory} from core's internal API. core's 403 for an account that is not a
 * caregiver's reaches the caller as the {@code AccessDeniedException} it was; its 404 becomes the
 * not-found core threw in process.
 */
@Component
class CoreCaregiverWorkDirectory implements CaregiverWorkDirectory {

	private final CoreApi core;

	CoreCaregiverWorkDirectory(CoreApi core) {
		this.core = core;
	}

	@Override
	public Profile require(String username) {
		try {
			CoreApi.CaregiverProfile profile = core.caregiverByUsername(username);
			return new Profile(profile.id(), profile.userId(), profile.fullName(), profile.phone(), profile.sector(),
					profile.dialects(), profile.status());
		}
		catch (CoreNotFound missing) {
			throw new ResourceNotFound("Caregiver profile for user", username);
		}
	}

	@Override
	public ElderView elder(Long elderId) {
		try {
			CoreApi.ElderView elder = core.elderCaregiverView(elderId);
			return new ElderView(elder.elderId(), elder.preferredName(), elder.serviceAddress(), elder.postalSector(),
					elder.languageNeeds(), elder.accessNotes(), elder.emergencyNotes());
		}
		catch (CoreNotFound missing) {
			throw new ResourceNotFound("Elder", elderId);
		}
	}

	@Override
	public CredentialAlerts alerts(Long caregiverId, LocalDate today) {
		CoreApi.CredentialAlerts alerts = core.credentialAlerts(caregiverId, today);
		return new CredentialAlerts(alerts.items().stream()
				.map(alert -> new CredentialAlert(alert.id(), alert.name(), alert.certificateNo(), alert.expiryDate(),
						alert.status(), alert.warning(), alert.daysUntilExpiry(), alert.renewalState(),
						alert.renewalValidFrom()))
				.toList(),
				new CredentialAlertContext(alerts.context().asOfDate(), alerts.context().warningDays(),
						alerts.context().reviewRequired()));
	}

}
