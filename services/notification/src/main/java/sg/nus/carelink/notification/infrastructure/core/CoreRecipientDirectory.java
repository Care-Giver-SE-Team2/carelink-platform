package sg.nus.carelink.notification.infrastructure.core;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.notification.domain.model.InboxReader;
import sg.nus.carelink.notification.domain.repository.RecipientDirectory;
import sg.nus.carelink.shared.security.Role;

/**
 * The inbox's reader, as core knows the account now: enabled, with a current role, and for a family
 * member the elders they may read. Asked on every inbox call, so a session that outlived its account's
 * role or binding opens nothing it no longer may.
 */
@Component
class CoreRecipientDirectory implements RecipientDirectory {

	private static final Set<String> ROLES = Arrays.stream(Role.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

	private static final String FAMILY = Role.FAMILY.name();

	private final CoreApi core;

	CoreRecipientDirectory(CoreApi core) {
		this.core = core;
	}

	@Override
	public Optional<InboxReader> readerOf(String username, boolean familySession) {
		return core.findAccount(username)
				.filter(CoreApi.Account::enabled)
				.filter(account -> account.roles().stream().anyMatch(ROLES::contains))
				.filter(account -> !familySession || account.roles().contains(FAMILY))
				.map(account -> account.roles().contains(FAMILY)
						? InboxReader.family(account.userId(), readableElders(username))
						: InboxReader.own(account.userId()));
	}

	/** A family account without a family profile may read no elder; its account-only messages it still sees. */
	private Set<Long> readableElders(String username) {
		try {
			return core.readableElders(username).elderIds();
		}
		catch (AccessDeniedException noFamilyProfile) {
			return Set.of();
		}
	}
}
