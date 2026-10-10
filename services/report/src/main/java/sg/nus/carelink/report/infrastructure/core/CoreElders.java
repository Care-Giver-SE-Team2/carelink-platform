package sg.nus.carelink.report.infrastructure.core;

import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.report.application.Elders;
import sg.nus.carelink.shared.error.ResourceNotFound;

/** {@link Elders} from core's internal API. core's 404 becomes the 404 core itself answered before. */
@Component
class CoreElders implements Elders {

	private final CoreApi core;

	CoreElders(CoreApi core) {
		this.core = core;
	}

	@Override
	public Long requireElderIdOfUser(Long userId) {
		try {
			return core.elderByUser(userId).elderId();
		}
		catch (CoreNotFound missing) {
			throw new ResourceNotFound("Elder for user", userId);
		}
	}

}
