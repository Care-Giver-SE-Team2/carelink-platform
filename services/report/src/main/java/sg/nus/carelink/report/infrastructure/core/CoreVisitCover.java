package sg.nus.carelink.report.infrastructure.core;

import java.util.List;

import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.coreapi.CoreRuleViolation;
import sg.nus.carelink.report.application.VisitCover;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * {@link VisitCover} from core's internal API: rostering ranks the caregivers and puts one on the
 * visit. A refusal keeps rostering's code, so the manager sees the same answer as before.
 */
@Component
class CoreVisitCover implements VisitCover {

	private final CoreApi core;

	CoreVisitCover(CoreApi core) {
		this.core = core;
	}

	@Override
	public List<Option> options(Long visitId) {
		try {
			return core.coverOptions(visitId).stream()
					.map(option -> new Option(option.caregiverId(), option.name(), option.rank(), option.reason()))
					.toList();
		}
		catch (CoreNotFound missing) {
			throw new ResourceNotFound("Visit", visitId);
		}
	}

	@Override
	public void cover(Long visitId, Long caregiverId, Long byUserId) {
		try {
			core.cover(visitId, new CoreApi.CoverRequest(caregiverId, byUserId));
		}
		catch (CoreNotFound missing) {
			throw new ResourceNotFound("Visit", visitId);
		}
		catch (CoreRuleViolation refused) {
			throw new BusinessRuleViolation(refused.code(), refused.getMessage());
		}
	}

}
