package sg.nus.carelink.incident.infrastructure.directory;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.incident.domain.model.Responder;
import sg.nus.carelink.incident.domain.repository.ManagerDirectory;
import sg.nus.carelink.shared.security.Role;

/**
 * Answers "who can take an incident" by reading the account tables directly.
 *
 * <p><strong>Why two plain queries rather than a call into the identity module.</strong>
 * The chain needs one thing identity does not expose: the enabled accounts holding a role.
 * Adding it there would mean editing a module two other people build on - a port, its
 * adapter, the Spring Data interface, the service and the shared test fake - for the sake
 * of one caller. Reading is not owning: these two statements take nothing but an id and a
 * name, write nothing, and can be deleted the day identity offers the lookup itself.
 *
 * <p>Kept deliberately small for that reason. It is a projection, not a second mapping of
 * {@code app_user}: no entity, no repository, nothing that could drift from the identity
 * module's own view of the same rows.
 */
@Component
class AccountManagerDirectory implements ManagerDirectory {

	private static final String MANAGERS = """
			select u.id, u.display_name
			from app_user u
			join user_role r on r.user_id = u.id
			where u.enabled = true and r.role = :role
			order by u.display_name, u.id
			""";

	private static final String BY_ID = """
			select id, display_name from app_user where id = :id
			""";

	private final JdbcClient jdbc;

	AccountManagerDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Responder> allManagers() {
		return jdbc.sql(MANAGERS)
				.param("role", Role.MANAGER.name())
				.query((rs, rowNum) -> new Responder(rs.getLong("id"), rs.getString("display_name")))
				.list();
	}

	@Override
	public Optional<Responder> responderById(Long userId) {
		if (userId == null) {
			return Optional.empty();
		}
		return jdbc.sql(BY_ID)
				.param("id", userId)
				.query((rs, rowNum) -> new Responder(rs.getLong("id"), rs.getString("display_name")))
				.optional();
	}
}
