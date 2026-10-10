package sg.nus.carelink.report.infrastructure.core;

import java.util.Optional;

import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.report.application.CaregiverDirectory;

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

}
