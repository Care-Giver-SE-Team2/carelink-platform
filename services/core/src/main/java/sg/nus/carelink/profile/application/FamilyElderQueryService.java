package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Queries elder summaries within the current family's readable bindings.
 *
 * @author Wang Zhili
 */
@Service
public class FamilyElderQueryService {

	private final FamilyAccessQuery access;
	private final ProfileService profiles;
	private final Clock clock;
	private final FamilyReadAudit audit;

	public FamilyElderQueryService(FamilyAccessQuery access, ProfileService profiles, Clock clock, FamilyReadAudit audit) {
		this.access = access;
		this.profiles = profiles;
		this.clock = clock;
		this.audit = audit;
	}

	/**
	 * Lists elders covered by the authenticated family's current read permission.
	 *
	 * @param authenticatedUsername Username supplied by the authenticated session
	 * @return Readable elder summaries, or an empty list when no binding grants access
	 * @author Wang Zhili
	 */
	public List<ElderSummary> listForFamily(String authenticatedUsername) {
		return audit.read(authenticatedUsername, FamilyReadAudit.Resource.ELDERS, null, "", () -> {
			var elderIds = access.readableElderIds(authenticatedUsername);
			LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));
			return profiles.listEldersByIds(elderIds, today);
		});
	}
}
