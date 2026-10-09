package sg.nus.carelink.notification.infrastructure.lookup;

import java.util.Optional;
import java.util.Arrays;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.notification.domain.repository.RecipientDirectory;
import sg.nus.carelink.notification.domain.model.InboxReader;
import sg.nus.carelink.shared.security.Role;

/**
 * Reads {@code app_user}, which identity owns, with one plain statement - the way the other
 * modules' notifiers find their recipients - so this module needs nothing of identity's code.
 */
@Component
class JdbcRecipientDirectory implements RecipientDirectory {

	private final JdbcClient jdbc;

	JdbcRecipientDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<InboxReader> readerOf(String username, boolean familySession) {
		// Resolve current roles together with account enablement before any inbox mutation.
		return jdbc.sql("""
				select u.id, exists(select 1 from user_role r where r.user_id=u.id and r.role='FAMILY') as family
				from app_user u
				where u.username=:username and u.enabled=true
				  and exists(select 1 from user_role r where r.user_id=u.id and r.role in (:roles))
				  and (:familySession=false or exists(select 1 from user_role r where r.user_id=u.id and r.role='FAMILY'))
				""")
				.param("username", username)
				.param("familySession", familySession)
				.param("roles", Arrays.stream(Role.values()).map(Enum::name).toList())
				.query((rs, row) -> new InboxReader(rs.getLong("id"), rs.getBoolean("family")))
				.optional();
	}
}
