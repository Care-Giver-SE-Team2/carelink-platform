package sg.nus.carelink.report.infrastructure.core;

import java.util.Set;

import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.report.application.FamilyAccessQuery;

/**
 * {@link FamilyAccessQuery} from core's internal API. core answers 403 where the account may not
 * read or act for the elder, and the client turns that into the {@code AccessDeniedException}
 * report has always thrown.
 */
@Component
class CoreFamilyAccessQuery implements FamilyAccessQuery {

	private final CoreApi core;

	CoreFamilyAccessQuery(CoreApi core) {
		this.core = core;
	}

	@Override
	public Set<Long> readableElderIds(String authenticatedUsername) {
		return core.readableElders(authenticatedUsername).elderIds();
	}

	@Override
	public void requireReadableElder(String authenticatedUsername, Long elderId) {
		core.checkElderAccess(authenticatedUsername, elderId, CoreApi.Access.READ);
	}

	@Override
	public void requireWritableElder(String authenticatedUsername, Long elderId) {
		core.checkElderAccess(authenticatedUsername, elderId, CoreApi.Access.WRITE);
	}

}
