package sg.nus.carelink.report.infrastructure.core;

import java.util.Optional;

import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.report.application.FamilyMembers;

/** {@link FamilyMembers} from core's internal API. */
@Component
class CoreFamilyMembers implements FamilyMembers {

	private final CoreApi core;

	CoreFamilyMembers(CoreApi core) {
		this.core = core;
	}

	@Override
	public Optional<Long> findIdByUserId(Long userId) {
		return core.findFamilyMemberIdByUser(userId);
	}

}
