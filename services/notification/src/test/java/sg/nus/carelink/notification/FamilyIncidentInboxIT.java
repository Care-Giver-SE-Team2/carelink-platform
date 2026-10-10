package sg.nus.carelink.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * FM05 consumer messages through the inbox with current authorization and Singapore time. Each
 * message is written the way incident writes it when it raises an incident ({@code publish}), and a
 * signed-in session keeps the roles its account had at sign-in, as core's sign-in leaves it.
 * @author Wang Zhili
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FamilyIncidentInboxIT.TimeConfiguration.class)
class FamilyIncidentInboxIT {
	private static final Instant START = Instant.parse("2026-10-07T08:00:00Z");
	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private InboxClock clock;
	private final JsonMapper json = JsonMapper.builder().build();

	@DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyIncidentInboxIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
	}
	@TestConfiguration(proxyBeanMethods = false)
	static class TimeConfiguration {
		@Bean @Primary InboxClock inboxClock() { return new InboxClock(); }
	}
	static class InboxClock extends Clock {
		volatile Instant now = START;
		volatile ZoneId zone = ZoneId.of("Asia/Singapore");
		@Override public ZoneId getZone() { return zone; }
		@Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
		@Override public Instant instant() { return now; }
	}

	@BeforeEach void prepare() {
		clock.now = START;
		clock.zone = ZoneId.of("Asia/Singapore");
		for (String table : List.of("family_alert_window", "family_alert_delivery", "family_alert_event", "audit_log",
				"notification_subscription", "notification", "incident_acknowledgement", "incident_log", "incident",
				"elder_family_binding", "elder", "family_member", "user_role", "app_user")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}test-password', 'Family A'), (9, 'family-b', '{noop}test-password', 'Family B'),
				(10, 'manager', '{noop}test-password', 'Manager')
				""");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES (7,'FAMILY'),(9,'FAMILY'),(10,'MANAGER')");
		jdbc.update("INSERT INTO family_member (id,user_id,full_name) VALUES (42,7,'Family A'),(7,9,'Family B')");
		jdbc.update("INSERT INTO elder (id,full_name) VALUES (101,'Elder A'),(102,'Elder B'),(103,'Elder C')");
		jdbc.update("""
				INSERT INTO elder_family_binding (elder_id,family_member_id,access_scope,status) VALUES
				(101,42,'FULL','ACTIVE'),(102,42,'READ_ONLY','ACTIVE'),(101,7,'FULL','ACTIVE')
				""");
		for (long id : List.of(601L, 602L, 603L)) {
			jdbc.update("""
					INSERT INTO incident (id,elder_id,responder_user_id,source,category,severity,status,description,reported_at,respond_by)
					VALUES (?,?,10,'CAREGIVER','FALL','HIGH','OPEN','Private clinical detail',?,?)
					""", id, id - 500, time(15,0), time(15,5));
		}
	}

	@Test void observerMessagesAppearOnlyInTheRecipientsInboxWithSafePayloadAndIndependentDelivery() throws Exception {
		publish(601);
		Browser a = login("family-a"), b = login("family-b");
		long own = notification(7,601), other = notification(9,601);
		var windows = rows("family_alert_window");
		clock.now = START.plusSeconds(600);
		JsonNode page = fetch(a,"/api/notifications/me");
		assertThat(ids(page)).containsExactly(own);
		JsonNode item = page.path("items").get(0);
		assertThat(item.path("elderId").asLong()).isEqualTo(101);
		assertThat(item.path("resourceId").asLong()).isEqualTo(601);
		assertThat(item.path("resourceType").asString()).isEqualTo("INCIDENT");
		assertThat(item.path("channel").asString()).isEqualTo("IN_APP");
		assertThat(item.path("eventType").asString()).isEqualTo("INCIDENT_RAISED");
		assertThat(item.path("status").asString()).isEqualTo("SENT");
		assertThat(item.path("createdAt").asString()).isEqualTo("2026-10-07T16:00:00+08:00");
		assertThat(item.path("sentAt").asString()).isEqualTo("2026-10-07T16:10:00+08:00");
		assertThat(item.path("readAt").isNull()).isTrue();
		assertThat(item.toString()).doesNotContain("Private clinical", "description", "responderUserId");
		assertThat(state(other)).isEqualTo("PENDING");
		assertThat(fetch(b,"/api/notifications/me/unread-count").path("unread").asLong()).isEqualTo(1);
		assertThat(rows("family_alert_window")).isEqualTo(windows);
		assertThat(rows("incident_acknowledgement")).isEmpty();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_user_id=7 AND resource_type='notification_inbox' AND result='OK'",Long.class)).isEqualTo(1);
	}

	@Test void scopeIsAppliedBeforePaginationAndCountsWithStableTimeAndIdOrdering() throws Exception {
		publish(601); publish(602);
		long first = notification(7,601), second = notification(7,602);
		long hidden = manual(7,"INCIDENT",603L,"IN_APP","PENDING");
		manual(9,"INCIDENT",601L,"IN_APP","PENDING");
		manual(7,"INCIDENT",601L,"EMAIL","PENDING");
		manual(7,"INCIDENT",601L,"IN_APP","FAILED");
		long missing = manual(7,"INCIDENT",999L,"IN_APP","PENDING");
		jdbc.update("UPDATE notification SET created_at=? WHERE id IN (?,?)",time(16,0),hidden,missing);
		Browser a = login("family-a");
		JsonNode page0 = fetch(a,"/api/notifications/me?page=0&size=1");
		assertThat(ids(page0)).containsExactly(second);
		assertThat(page0.path("totalElements").asLong()).isEqualTo(2);
		assertThat(ids(fetch(a,"/api/notifications/me?page=1&size=1"))).containsExactly(first);
		assertThat(ids(fetch(a,"/api/notifications/me?page=2&size=1"))).isEmpty();
		assertThat(fetch(a,"/api/notifications/me/unread-count").path("unread").asLong()).isEqualTo(2);
		jdbc.update("UPDATE notification SET created_at=? WHERE id=?",time(15,59),second);
		assertThat(ids(fetch(a,"/api/notifications/me"))).containsExactly(first,second);
		read(a,first);
		assertThat(ids(fetch(a,"/api/notifications/me?status=READ"))).containsExactly(first);
		assertThat(ids(fetch(a,"/api/notifications/me?status=SENT"))).containsExactly(second);
	}

	@Test void notificationReadIsIndependentOfViewAcknowledgementAndPersonalWindow() throws Exception {
		publish(601);
		Browser a=login("family-a"); long id=notification(7,601);
		var windows=rows("family_alert_window"); var incidents=rows("incident");
		clock.now=START.plusSeconds(600);
		JsonNode first=read(a,id);
		assertThat(first.path("readAt").asString()).isEqualTo("2026-10-07T16:10:00+08:00");
		assertThat(rows("incident_acknowledgement")).isEmpty();
		clock.now=START.plusSeconds(1200);
		assertThat(read(a,id).path("readAt")).isEqualTo(first.path("readAt"));
		assertThat(fetch(a,"/api/notifications/me/unread-count").path("unread").asLong()).isZero();
		assertThat(rows("family_alert_window")).isEqualTo(windows);
		assertThat(rows("incident")).isEqualTo(incidents);
	}

	@Test void incidentReceiptsDoNotMarkNotificationsReadAndReadAllPreservesThem() throws Exception {
		publish(601); publish(602);
		Browser a=login("family-a");
		// What the family's view and acknowledgement leave in incident.
		jdbc.update("INSERT INTO incident_acknowledgement (incident_id,family_member_id,viewed_at,acknowledged_at,response_note) VALUES (601,42,?,?,'I know')",time(16,0),time(16,1));
		var receipts=rows("incident_acknowledgement"); var windows=rows("family_alert_window");
		assertThat(state(notification(7,601))).isEqualTo("PENDING");
		assertThat(fetch(a,"/api/notifications/me/unread-count").path("unread").asLong()).isEqualTo(2);
		assertThat(submit(a,"/api/notifications/me/read-all").path("updated").asInt()).isEqualTo(2);
		assertThat(submit(a,"/api/notifications/me/read-all").path("updated").asInt()).isZero();
		assertThat(rows("incident_acknowledgement")).isEqualTo(receipts);
		assertThat(rows("family_alert_window")).isEqualTo(windows);
		assertThat(state(notification(9,601))).isEqualTo("PENDING");
	}

	@ParameterizedTest @ValueSource(strings={"REVOKED","REJECTED","PENDING_CONFIRMATION","EXPIRED","DELETED"})
	void changedBindingHidesHistoricalNoticeAndReadAllTouchesOnlyStillVisibleMessages(String change) throws Exception {
		publish(601); publish(602);
		Browser a=login("family-a"); long hidden=notification(7,601), visible=notification(7,602);
		fetch(a,"/api/notifications/me");
		if (change.equals("EXPIRED")) { jdbc.update("UPDATE elder_family_binding SET expires_at=? WHERE elder_id=101 AND family_member_id=42",time(16,0)); }
		else if (change.equals("DELETED")) { jdbc.update("DELETE FROM elder_family_binding WHERE elder_id=101 AND family_member_id=42"); }
		else { jdbc.update("UPDATE elder_family_binding SET status=? WHERE elder_id=101 AND family_member_id=42",change); }
		assertThat(ids(fetch(a,"/api/notifications/me?size=1"))).containsExactly(visible);
		assertThat(fetch(a,"/api/notifications/me?size=1").path("totalElements").asLong()).isEqualTo(1);
		assertThat(fetch(a,"/api/notifications/me/unread-count").path("unread").asLong()).isEqualTo(1);
		mvc.perform(write(a,"/api/notifications/"+hidden+"/read")).andExpect(status().isNotFound());
		assertThat(submit(a,"/api/notifications/me/read-all").path("updated").asInt()).isEqualTo(1);
		assertThat(state(hidden)).isEqualTo("SENT");
		assertThat(state(visible)).isEqualTo("READ");
	}

	@Test void otherRecipientsNoticeAndOrphanIncidentCannotBeOpened() throws Exception {
		publish(601);
		Browser a=login("family-a");
		long own=notification(7,601), other=notification(9,601);
		mvc.perform(write(a,"/api/notifications/"+other+"/read")).andExpect(status().isNotFound());
		jdbc.update("DELETE FROM incident WHERE id=601");
		assertThat(ids(fetch(a,"/api/notifications/me"))).isEmpty();
		assertThat(fetch(a,"/api/notifications/me/unread-count").path("unread").asLong()).isZero();
		mvc.perform(write(a,"/api/notifications/"+own+"/read")).andExpect(status().isNotFound());
		assertThat(submit(a,"/api/notifications/me/read-all").path("updated").asInt()).isZero();
	}

	@ParameterizedTest @ValueSource(strings={"PENDING","FAILED","UNKNOWN"})
	void invalidFamilyStatusFiltersAreRejected(String value) throws Exception {
		mvc.perform(get("/api/notifications/me").param("status",value).with(login("family-a").signedIn())).andExpect(status().isBadRequest());
	}

	@Test void sessionAndCsrfAreRequiredForInboxCommands() throws Exception {
		mvc.perform(get("/api/notifications/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/notifications/me/unread-count")).andExpect(status().isUnauthorized());
		Browser a=login("family-a"); publish(601); long id=notification(7,601);
		mvc.perform(post("/api/notifications/{id}/read",id).with(a.signedIn())).andExpect(status().isForbidden());
		mvc.perform(post("/api/notifications/me/read-all").with(a.signedIn())).andExpect(status().isForbidden());
		assertThat(state(id)).isEqualTo("PENDING");
	}

	@Test void concurrentReadCommandsPreserveTheFirstReadAndNeverCreateIncidentReceipts() throws Exception {
		publish(601); Browser a=login("family-a"); long id=notification(7,601);
		fetch(a,"/api/notifications/me/unread-count");
		var start=new CountDownLatch(1);
		try(var pool=Executors.newFixedThreadPool(4)) {
			var futures=new ArrayList<java.util.concurrent.Future<?>>();
			for(int n=0;n<4;n++) { futures.add(pool.submit(()-> { assertThat(start.await(10,TimeUnit.SECONDS)).isTrue(); read(a,id); return null; })); }
			start.countDown();
			for(var future:futures) { future.get(20,TimeUnit.SECONDS); }
		}
		JsonNode first=read(a,id);
		assertThat(first.path("status").asString()).isEqualTo("READ");
		clock.now=START.plusSeconds(60);
		assertThat(read(a,id).path("readAt")).isEqualTo(first.path("readAt"));
		assertThat(rows("incident_acknowledgement")).isEmpty();
	}

	@Test void emptyInboxReturnsAnEmptyPageAndNoReadAllUpdates() throws Exception {
		Browser a=login("family-a");
		JsonNode page=fetch(a,"/api/notifications/me");
		assertThat(ids(page)).isEmpty();
		assertThat(page.path("totalElements").asLong()).isZero();
		assertThat(fetch(a,"/api/notifications/me/unread-count").path("unread").asLong()).isZero();
		assertThat(submit(a,"/api/notifications/me/read-all").path("updated").asInt()).isZero();
		assertThat(rows("incident_acknowledgement")).isEmpty();
	}

	@Test void existingStaffInboxStillHandlesItsOwnMessagesWithoutTouchingFamilyMessages() throws Exception {
		publish(601);
		var families=jdbc.queryForList("SELECT * FROM notification WHERE recipient_user_id IN (7,9) ORDER BY id");
		long id=manual(10,"INCIDENT",601L,"IN_APP","PENDING");
		Browser manager=login("manager");
		assertThat(ids(fetch(manager,"/api/notifications/me"))).containsExactly(id);
		assertThat(read(manager,id).path("status").asString()).isEqualTo("READ");
		assertThat(submit(manager,"/api/notifications/me/read-all").path("updated").asInt()).isZero();
		assertThat(jdbc.queryForList("SELECT * FROM notification WHERE recipient_user_id IN (7,9) ORDER BY id")).isEqualTo(families);
		assertThat(rows("incident_acknowledgement")).isEmpty();
	}

	@ParameterizedTest @ValueSource(strings={"MANAGER","CAREGIVER","ELDER"})
	void otherCurrentRolesRetainTheirOwnAccountAndStaffResourceMessages(String role) throws Exception {
		publish(601); var families=rows("notification");
		jdbc.update("UPDATE user_role SET role=? WHERE user_id=10",role);
		long credential=manual(10,"CREDENTIAL",999L,"IN_APP","PENDING");
		long absence=manual(10,"ABSENCE",999L,"IN_APP","PENDING");
		Browser staff=login("manager");
		assertThat(ids(fetch(staff,"/api/notifications/me"))).containsExactly(absence,credential);
		assertThat(fetch(staff,"/api/notifications/me/unread-count").path("unread").asLong()).isEqualTo(2);
		assertThat(read(staff,credential).path("status").asString()).isEqualTo("READ");
		assertThat(submit(staff,"/api/notifications/me/read-all").path("updated").asInt()).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT * FROM notification WHERE recipient_user_id IN (7,9) ORDER BY id")).isEqualTo(families);
	}

	@ParameterizedTest @ValueSource(strings={"DISABLED","ROLE_REMOVED","ROLE_REMOVED_WITH_MANAGER","ACCOUNT_DELETED"})
	void existingSessionCannotUseAnyInboxOperationAfterLosingAuthorization(String change) throws Exception {
		if(change.equals("ROLE_REMOVED_WITH_MANAGER")) { jdbc.update("INSERT INTO user_role(user_id,role) VALUES (7,'MANAGER')"); }
		publish(601); publish(602); Browser a=login("family-a"); long id=notification(7,601);
		var notifications=rows("notification"); var windows=rows("family_alert_window");
		if(change.equals("DISABLED")) { jdbc.update("UPDATE app_user SET enabled=false WHERE id=7"); }
		else if(change.equals("ACCOUNT_DELETED")) {
			jdbc.update("UPDATE family_member SET user_id=NULL WHERE id=42");
			jdbc.update("DELETE FROM user_role WHERE user_id=7");
			jdbc.update("DELETE FROM app_user WHERE id=7");
		} else { jdbc.update("DELETE FROM user_role WHERE user_id=7 AND role='FAMILY'"); }
		for(String path:List.of("/api/notifications/me","/api/notifications/me/unread-count")) {
			mvc.perform(get(path).with(a.signedIn())).andExpect(status().isForbidden());
		}
		mvc.perform(write(a,"/api/notifications/"+id+"/read")).andExpect(status().isForbidden());
		mvc.perform(write(a,"/api/notifications/me/read-all")).andExpect(status().isForbidden());
		assertThat(rows("notification")).isEqualTo(notifications);
		assertThat(rows("family_alert_window")).isEqualTo(windows);
		assertThat(rows("incident_acknowledgement")).isEmpty();
	}

	@Test void unknownAndOrphanedResourcesAreExcludedBeforePagingCountsAndWrites() throws Exception {
		publish(601); long visible=notification(7,601);
		List<Long> excluded=new ArrayList<>();
		excluded.add(manual(7,"UNKNOWN_CARE",999L,"IN_APP","PENDING"));
		excluded.add(manual(7,"INCIDENT",999L,"IN_APP","PENDING"));
		excluded.add(manual(7,null,999L,"IN_APP","PENDING"));
		excluded.add(manual(7,"ACCOUNT",null,"IN_APP","PENDING"));
		excluded.add(manual(7,"UNKNOWN_CARE",null,"IN_APP","PENDING"));
		long account=manual(7,null,null,"IN_APP","PENDING");
		Browser a=login("family-a");
		JsonNode page=fetch(a,"/api/notifications/me?size=1");
		assertThat(ids(page)).containsExactly(account);
		assertThat(page.path("totalElements").asLong()).isEqualTo(2);
		assertThat(ids(fetch(a,"/api/notifications/me?page=1&size=1"))).containsExactly(visible);
		assertThat(fetch(a,"/api/notifications/me/unread-count").path("unread").asLong()).isEqualTo(2);
		for(long id:excluded) { mvc.perform(write(a,"/api/notifications/"+id+"/read")).andExpect(status().isNotFound()); }
		assertThat(read(a,account).path("elderId").isNull()).isTrue();
		assertThat(submit(a,"/api/notifications/me/read-all").path("updated").asInt()).isEqualTo(1);
		for(long id:excluded) { assertThat(state(id)).isEqualTo("PENDING"); }
	}

	@ParameterizedTest @ValueSource(strings={"UTC","Asia/Singapore"})
	void clocksUseSingaporeExpiryDeliveryAndFirstReadTime(String zone) throws Exception {
		publish(601); publish(602); Browser a=login("family-a");
		long hidden=notification(7,601), visible=notification(7,602);
		jdbc.update("UPDATE elder_family_binding SET expires_at=? WHERE elder_id=101 AND family_member_id=42",time(16,0));
		clock.zone=ZoneId.of(zone);
		JsonNode page=fetch(a,"/api/notifications/me");
		assertThat(ids(page)).containsExactly(visible);
		assertThat(page.path("items").get(0).path("sentAt").asString()).isEqualTo("2026-10-07T16:00:00+08:00");
		assertThat(fetch(a,"/api/notifications/me/unread-count").path("unread").asLong()).isEqualTo(1);
		assertThat(state(hidden)).isEqualTo("PENDING");
		mvc.perform(write(a,"/api/notifications/"+hidden+"/read")).andExpect(status().isNotFound());
		clock.now=START.plusSeconds(60);
		assertThat(read(a,visible).path("readAt").asString()).isEqualTo("2026-10-07T16:01:00+08:00");
		assertThat(submit(a,"/api/notifications/me/read-all").path("updated").asInt()).isZero();
	}

	@ParameterizedTest @ValueSource(booleans={false,true})
	void currentFamilyRoleCannotBeBypassedByAStaffOrMultiRoleSession(boolean roleAtLogin) throws Exception {
		if(roleAtLogin) { jdbc.update("INSERT INTO user_role(user_id,role) VALUES (10,'FAMILY')"); }
		Browser manager=login("manager");
		if(!roleAtLogin) { jdbc.update("INSERT INTO user_role(user_id,role) VALUES (10,'FAMILY')"); }
		long care=manual(10,"INCIDENT",601L,"IN_APP","PENDING");
		long account=manual(10,null,null,"IN_APP","PENDING");
		assertThat(ids(fetch(manager,"/api/notifications/me"))).containsExactly(account);
		assertThat(fetch(manager,"/api/notifications/me/unread-count").path("unread").asLong()).isEqualTo(1);
		mvc.perform(get("/api/notifications/me?status=PENDING").with(manager.signedIn())).andExpect(status().isBadRequest());
		mvc.perform(write(manager,"/api/notifications/"+care+"/read")).andExpect(status().isNotFound());
		assertThat(submit(manager,"/api/notifications/me/read-all").path("updated").asInt()).isEqualTo(1);
		assertThat(state(care)).isEqualTo("PENDING");
	}

	@ParameterizedTest @ValueSource(strings={"DISABLED","ROLE_REMOVED"})
	void staffSessionAlsoRechecksCurrentAuthorization(String change) throws Exception {
		Browser manager=login("manager"); long id=manual(10,"CREDENTIAL",999L,"IN_APP","PENDING");
		if(change.equals("DISABLED")) { jdbc.update("UPDATE app_user SET enabled=false WHERE id=10"); }
		else { jdbc.update("DELETE FROM user_role WHERE user_id=10"); }
		mvc.perform(get("/api/notifications/me").with(manager.signedIn())).andExpect(status().isForbidden());
		mvc.perform(get("/api/notifications/me/unread-count").with(manager.signedIn())).andExpect(status().isForbidden());
		mvc.perform(write(manager,"/api/notifications/"+id+"/read")).andExpect(status().isForbidden());
		mvc.perform(write(manager,"/api/notifications/me/read-all")).andExpect(status().isForbidden());
		assertThat(state(id)).isEqualTo("PENDING");
	}

	@Test void aFailedReadChangesNothingAndOnlyAnExplicitRetrySavesIt() throws Exception {
		publish(601); Browser a=login("family-a"); long id=notification(7,601);
		fetch(a,"/api/notifications/me");
		var delivered=jdbc.queryForMap("SELECT * FROM notification WHERE id=?",id);
		jdbc.execute("CREATE TRIGGER fm05_inbox_read_fault BEFORE UPDATE ON notification FOR EACH ROW BEGIN IF NEW.id="+id
				+" AND NEW.status='READ' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic recipient write failure'; END IF; END");
		try {
			mvc.perform(write(a,"/api/notifications/"+id+"/read")).andExpect(status().isInternalServerError());
			assertThat(jdbc.queryForMap("SELECT * FROM notification WHERE id=?",id)).isEqualTo(delivered);
		} finally { jdbc.execute("DROP TRIGGER fm05_inbox_read_fault"); }
		clock.now=START.plusSeconds(360);
		JsonNode saved=read(a,id);
		assertThat(saved.path("readAt").asString()).isEqualTo("2026-10-07T16:06:00+08:00");
		clock.now=START.plusSeconds(420);
		assertThat(read(a,id)).isEqualTo(saved);
		assertThat(rows("incident_acknowledgement")).isEmpty();
	}

	@Test
	void outOfRangePageParametersUseExistingClampingPolicy() throws Exception {
		Browser a=login("family-a");
		JsonNode page=fetch(a,"/api/notifications/me?page=-1&size=0");
		assertThat(page.path("page").asInt()).isZero();
		assertThat(page.path("size").asInt()).isEqualTo(1);
		assertThat(fetch(a,"/api/notifications/me?size=201").path("size").asInt()).isEqualTo(200);
	}

	private record Browser(String username,List<String> roles) {
		RequestPostProcessor signedIn() { return user(username).roles(roles.toArray(String[]::new)); }
	}
	private Browser login(String username) {
		return new Browser(username,jdbc.queryForList("SELECT r.role FROM user_role r JOIN app_user u ON u.id=r.user_id WHERE u.username=?",String.class,username));
	}
	private JsonNode fetch(Browser browser,String path) throws Exception {
		return json.readTree(mvc.perform(get(path).with(browser.signedIn())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}
	private MockHttpServletRequestBuilder write(Browser browser,String path) {
		return post(path).with(browser.signedIn()).with(csrf());
	}
	private JsonNode submit(Browser browser,String path) throws Exception {
		return json.readTree(mvc.perform(write(browser,path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}
	private JsonNode read(Browser browser,long id) throws Exception { return submit(browser,"/api/notifications/"+id+"/read"); }
	private List<Long> ids(JsonNode page) {
		List<Long> result=new ArrayList<>(); page.path("items").forEach(item->result.add(item.path("id").asLong())); return result;
	}
	/** What incident writes when it raises the incident: a PENDING message and a response window for each bound family member. */
	private void publish(long incidentId) {
		LocalDateTime now=LocalDateTime.ofInstant(clock.now,SINGAPORE);
		for(var member:jdbc.queryForList("SELECT f.id,f.user_id FROM elder_family_binding b JOIN family_member f ON f.id=b.family_member_id WHERE b.elder_id=? AND b.status='ACTIVE' ORDER BY f.id",incidentId-500)) {
			long userId=((Number)member.get("user_id")).longValue();
			jdbc.update("INSERT INTO notification (recipient_user_id,event_type,channel,title,body,resource_type,resource_id,status,created_at) VALUES (?,'INCIDENT_RAISED','IN_APP','Urgent care alert: HIGH','A FALL incident has been reported. Open the incident details.','INCIDENT',?,'PENDING',?)",
					userId,incidentId,Timestamp.valueOf(now));
			jdbc.update("INSERT INTO family_alert_window (incident_id,family_member_id,first_notification_id,opened_at,acknowledge_by) VALUES (?,?,?,?,?)",
					incidentId,member.get("id"),notification(userId,incidentId),Timestamp.valueOf(now),Timestamp.valueOf(now.plusHours(2)));
		}
	}
	private long notification(long userId,long incidentId) {
		return jdbc.queryForObject("SELECT MIN(id) FROM notification WHERE recipient_user_id=? AND resource_type='INCIDENT' AND resource_id=?",Long.class,userId,incidentId);
	}
	private String state(long id) { return jdbc.queryForObject("SELECT status FROM notification WHERE id=?",String.class,id); }
	private List<Map<String,Object>> rows(String table) { return jdbc.queryForList("SELECT * FROM "+table); }
	private static Timestamp time(int hour,int minute) { return Timestamp.valueOf(LocalDateTime.of(2026,10,7,hour,minute)); }
	private long manual(long userId,String resourceType,Long resourceId,String channel,String state) {
		jdbc.update("INSERT INTO notification (recipient_user_id,event_type,channel,title,body,resource_type,resource_id,status,created_at) VALUES (?,'COMPATIBILITY',?,'Compatibility notice','Safe fixture',?,?,?,?)",
				userId,channel,resourceType,resourceId,state,time(16,0));
		return jdbc.queryForObject("SELECT MAX(id) FROM notification",Long.class);
	}
}
