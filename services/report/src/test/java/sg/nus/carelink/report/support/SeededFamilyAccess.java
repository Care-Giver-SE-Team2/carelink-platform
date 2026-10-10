package sg.nus.carelink.report.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import sg.nus.carelink.coreapi.CoreApi;

/**
 * core's answers about family access, for report's integration tests until the schema split.
 * report asks core whether a family account may read or act for an elder; here a
 * {@code CoreApi} test double answers from the accounts and bindings the test seeded into the
 * shared schema, by the rule core applies: an enabled FAMILY account with a family profile, and
 * a binding that is ACTIVE and not yet expired on Singapore's clock, FULL to act and FULL or
 * READ_ONLY to read. core's own tests prove that rule; here it only stands in for core, so that
 * report's tests can change a binding in the middle of a session and see report ask again.
 */
public final class SeededFamilyAccess {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	private final JdbcTemplate jdbc;
	private final Clock clock;

	private SeededFamilyAccess(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Makes {@code core} answer {@code readableElders} and {@code checkElderAccess} from the seeded rows. */
	public static void answer(CoreApi core, JdbcTemplate jdbc, Clock clock) {
		SeededFamilyAccess access = new SeededFamilyAccess(jdbc, clock);
		when(core.readableElders(anyString()))
				.thenAnswer(call -> new CoreApi.ReadableElders(access.readable(call.getArgument(0))));
		doAnswer(call -> {
			access.check(call.getArgument(0), call.getArgument(1), call.getArgument(2));
			return null;
		}).when(core).checkElderAccess(anyString(), anyLong(), any());
	}

	private Set<Long> readable(String username) {
		long familyMemberId = requireFamilyMemberId(username);
		return bindings(familyMemberId, null).stream()
				.filter(binding -> binding.allowsRead(now()))
				.map(Binding::elderId)
				.collect(Collectors.toUnmodifiableSet());
	}

	private void check(String username, Long elderId, CoreApi.Access access) {
		long familyMemberId = requireFamilyMemberId(username);
		boolean allowed = elderId != null && elderId > 0 && bindings(familyMemberId, elderId).stream()
				.anyMatch(binding -> access == CoreApi.Access.WRITE ? binding.allowsWrite(now()) : binding.allowsRead(now()));
		if (!allowed) {
			throw new AccessDeniedException(access == CoreApi.Access.WRITE
					? "A writable elder binding is required" : "A readable elder binding is required");
		}
	}

	private long requireFamilyMemberId(String username) {
		List<Long> family = jdbc.queryForList("""
				select f.id from family_member f
				join app_user u on u.id = f.user_id
				join user_role r on r.user_id = u.id and r.role = 'FAMILY'
				where u.username = ? and u.enabled = true
				""", Long.class, username);
		if (family.isEmpty()) {
			throw new AccessDeniedException("An enabled family account with a family profile is required");
		}
		return family.getFirst();
	}

	private List<Binding> bindings(long familyMemberId, Long elderId) {
		return jdbc.query("""
				select elder_id, access_scope, status, expires_at from elder_family_binding
				where family_member_id = ? and (? is null or elder_id = ?)
				""", (row, n) -> new Binding(row.getLong("elder_id"), row.getString("access_scope"),
				row.getString("status"), row.getObject("expires_at", LocalDateTime.class)),
				familyMemberId, elderId, elderId);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock.withZone(SINGAPORE));
	}

	private record Binding(long elderId, String scope, String status, LocalDateTime expiresAt) {

		boolean allowsRead(LocalDateTime at) {
			return "ACTIVE".equals(status) && ("FULL".equals(scope) || "READ_ONLY".equals(scope))
					&& (expiresAt == null || expiresAt.isAfter(at));
		}

		boolean allowsWrite(LocalDateTime at) {
			return "ACTIVE".equals(status) && "FULL".equals(scope) && (expiresAt == null || expiresAt.isAfter(at));
		}

	}

}
