package sg.nus.carelink.identity.infrastructure.lookup;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sg.nus.carelink.identity.application.AccountsByRole;
import sg.nus.carelink.shared.security.Role;

/** {@link AccountsByRole} with the statement the value-added manager alert runs today. */
@Component
class JdbcAccountsByRole implements AccountsByRole {

	private static final String ENABLED_WITH_ROLE = """
			select distinct u.id from app_user u
			join user_role r on r.user_id = u.id
			where r.role = :role and u.enabled = true
			order by u.id
			""";

	private final JdbcClient jdbc;

	JdbcAccountsByRole(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Long> enabledIdsWithRole(Role role) {
		return jdbc.sql(ENABLED_WITH_ROLE).param("role", role.name()).query(Long.class).list();
	}

}
