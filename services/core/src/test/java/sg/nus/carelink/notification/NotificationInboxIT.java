package sg.nus.carelink.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The inbox against a real MySQL, through the real security chain. Rows are written the way every
 * module's notifier writes them - PENDING, with a JDBC Timestamp from the Singapore clock - and
 * come back delivered, to their recipient only, newest first, at the wall-clock time they were
 * written with the Singapore offset. A family member sees a message about an elder only while the
 * binding to that elder is active, and each family read goes to the access audit.
 *
 * @author Wang Ziyu
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationInboxIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, NotificationInboxIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 7, 9, 0, 5);

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void staffReadOnlyTheirOwnMessagesDeliveredOnAskingAndOpeningThemIsRemembered() throws Exception {
		long alice = account("it-inbox-alice", "MANAGER");
		long ben = account("it-inbox-ben", "MANAGER");
		long older = write(alice, "Older", NINE, null, null);
		long newer = write(alice, "Newer", NINE.plusHours(1), null, null);
		long bens = write(ben, "Not Alice's", NINE.plusHours(2), null, null);

		String inbox = read("it-inbox-alice", "MANAGER", "/api/notifications/me");
		assertThat(JsonPath.<List<String>>read(inbox, "$.items[*].title")).containsExactly("Newer", "Older");
		assertThat(JsonPath.<List<String>>read(inbox, "$.items[*].status")).containsOnly("SENT");
		assertThat(JsonPath.<String>read(inbox, "$.items[1].createdAt"))
				.as("written from the Singapore clock, shown at the same wall-clock time with its offset")
				.isEqualTo("2026-10-07T09:00:05+08:00");
		assertThat(unread("it-inbox-alice", "MANAGER")).isEqualTo(2);

		mvc.perform(post("/api/notifications/" + newer + "/read").with(user("it-inbox-alice").roles("MANAGER")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READ"));
		mvc.perform(post("/api/notifications/" + bens + "/read").with(user("it-inbox-alice").roles("MANAGER")).with(csrf()))
				.andExpect(status().isNotFound());
		assertThat(unread("it-inbox-alice", "MANAGER")).isEqualTo(1);

		mvc.perform(post("/api/notifications/me/read-all").with(user("it-inbox-alice").roles("MANAGER")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updated").value(1));
		assertThat(unread("it-inbox-alice", "MANAGER")).isZero();
		assertThat(jdbc.queryForObject("select status from notification where id = ?", String.class, older))
				.isEqualTo("READ");
		assertThat(jdbc.queryForObject("select status from notification where id = ?", String.class, bens))
				.as("nobody asked for Ben's inbox, so it was not delivered")
				.isEqualTo("PENDING");
	}

	@Test
	void aFamilyMemberSeesMessagesAboutAnElderOnlyWhileBoundAndEachReadIsAudited() throws Exception {
		long fiona = account("it-inbox-fiona", "FAMILY");
		long elder = elder();
		long binding = bind(elder, familyMember(fiona));
		long aboutElder = write(fiona, "About the elder", NINE, "SPOT_CHECK", spotCheck(elder));
		write(fiona, "About the account", NINE.plusHours(1), null, null);

		String bound = read("it-inbox-fiona", "FAMILY", "/api/notifications/me");
		assertThat(JsonPath.<List<String>>read(bound, "$.items[*].title"))
				.containsExactly("About the account", "About the elder");
		assertThat(JsonPath.<Integer>read(bound, "$.items[1].elderId")).isEqualTo((int) elder);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where actor_user_id = ? "
				+ "and resource_type = 'notification_inbox'", Integer.class, fiona)).isEqualTo(1);

		jdbc.update("update elder_family_binding set status = 'REVOKED' where id = ?", binding);

		String revoked = read("it-inbox-fiona", "FAMILY", "/api/notifications/me");
		assertThat(JsonPath.<List<String>>read(revoked, "$.items[*].title")).containsExactly("About the account");
		assertThat(unread("it-inbox-fiona", "FAMILY")).isEqualTo(1);
		mvc.perform(post("/api/notifications/" + aboutElder + "/read").with(user("it-inbox-fiona").roles("FAMILY"))
						.with(csrf()))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/notifications/me").param("status", "PENDING").with(user("it-inbox-fiona").roles("FAMILY")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void withoutASessionThereIsNoInbox() throws Exception {
		mvc.perform(get("/api/notifications/me/unread-count")).andExpect(status().isUnauthorized());
	}

	private String read(String username, String role, String path) throws Exception {
		return mvc.perform(get(path).with(user(username).roles(role)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private int unread(String username, String role) throws Exception {
		return JsonPath.<Integer>read(read(username, role, "/api/notifications/me/unread-count"), "$.unread");
	}

	private long account(String username, String role) {
		jdbc.update("insert into app_user (username, password_hash, display_name) values (?, '{noop}unused', ?)",
				username, username);
		long id = jdbc.queryForObject("select id from app_user where username = ?", Long.class, username);
		jdbc.update("insert into user_role(user_id, role) values (?, ?)", id, role);
		return id;
	}

	private long elder() {
		jdbc.update("insert into elder (full_name) values ('Inbox Elder')");
		return jdbc.queryForObject("select max(id) from elder", Long.class);
	}

	private long familyMember(long userId) {
		jdbc.update("insert into family_member (user_id, full_name) values (?, 'Inbox Family')", userId);
		return jdbc.queryForObject("select id from family_member where user_id = ?", Long.class, userId);
	}

	private long bind(long elderId, long familyMemberId) {
		jdbc.update("insert into elder_family_binding (elder_id, family_member_id, status) values (?, ?, 'ACTIVE')",
				elderId, familyMemberId);
		return jdbc.queryForObject("select id from elder_family_binding where elder_id = ? and family_member_id = ?",
				Long.class, elderId, familyMemberId);
	}

	private long spotCheck(long elderId) {
		jdbc.update("insert into spot_check (elder_id, proposed_time, reason) values (?, ?, 'Routine')", elderId,
				Timestamp.valueOf(NINE.plusDays(3)));
		return jdbc.queryForObject("select max(id) from spot_check where elder_id = ?", Long.class, elderId);
	}

	private long write(long recipient, String title, LocalDateTime at, String resourceType, Long resourceId) {
		jdbc.update("insert into notification (recipient_user_id, event_type, channel, title, body, resource_type, "
				+ "resource_id, status, created_at) values (?, 'TEST_EVENT', 'IN_APP', ?, 'body', ?, ?, 'PENDING', ?)",
				recipient, title, resourceType, resourceId, Timestamp.valueOf(at));
		return jdbc.queryForObject("select max(id) from notification where recipient_user_id = ?", Long.class,
				recipient);
	}
}
