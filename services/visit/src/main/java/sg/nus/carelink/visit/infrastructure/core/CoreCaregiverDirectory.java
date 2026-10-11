package sg.nus.carelink.visit.infrastructure.core;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.visit.application.CaregiverDirectory;

/** {@link CaregiverDirectory} from core's internal API. */
@Component
class CoreCaregiverDirectory implements CaregiverDirectory {

	private final CoreApi core;

	CoreCaregiverDirectory(CoreApi core) {
		this.core = core;
	}

	@Override
	public Optional<CaregiverPublicProfile> findPublicProfile(Long caregiverId) {
		return core.findCaregiverPublicProfile(caregiverId)
				.map(profile -> new CaregiverPublicProfile(profile.id(), profile.fullName(), profile.dialects()));
	}

	@Override
	public List<CaregiverPublicCredential> listPublicCredentials(Long caregiverId) {
		return core.caregiverPublicCredentials(caregiverId).stream()
				.map(credential -> new CaregiverPublicCredential(credential.id(), credential.caregiverId(),
						credential.credentialTypeId(), credential.credentialTypeName(), credential.issuingBody(),
						credential.validFrom(), credential.expiryDate(), credential.status()))
				.toList();
	}

}
